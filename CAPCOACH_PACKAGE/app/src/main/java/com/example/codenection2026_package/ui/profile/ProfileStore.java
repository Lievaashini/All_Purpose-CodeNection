package com.example.codenection2026_package.ui.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stores the user's profile picture as a file in app-private storage.
 *
 * <p><b>Why the bytes are copied instead of keeping the picked {@link Uri}:</b> holding on
 * to the URI means holding on to a permission grant. The Android Photo Picker hands back a
 * URI that is readable for the current process, and while older devices can be talked into
 * a persistable grant through {@code takePersistableUriPermission}, that path behaves
 * differently on API 33+ (native picker) than on the {@code ACTION_OPEN_DOCUMENT} fallback
 * it uses below that. Copying the stream once removes the whole question: the picture then
 * survives restarts, reboots, the user clearing the Photos app, and the original file being
 * deleted - and it needs no read permission at any API level, because the picker already
 * granted read access to the one item it returned.
 *
 * <p>The file lives in {@code getFilesDir()}, so it is removed with the app on uninstall and
 * is never visible to other apps.
 */
public final class ProfileStore {

    private static final String TAG = "ProfileStore";

    private static final String FILE = "capcoach_profile";
    private static final String KEY_VERSION = "avatar_version";

    private static final String AVATAR_NAME = "profile_avatar.jpg";
    private static final String AVATAR_TEMP = "profile_avatar.jpg.tmp";

    private ProfileStore() {
    }

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static File avatarFileUnchecked(@NonNull Context c) {
        return new File(c.getApplicationContext().getFilesDir(), AVATAR_NAME);
    }

    /** @return the stored picture, or null when the user has not set one. */
    @Nullable
    public static File avatarFile(@NonNull Context c) {
        File file = avatarFileUnchecked(c);
        return file.isFile() ? file : null;
    }

    public static boolean hasAvatar(@NonNull Context c) {
        return avatarFile(c) != null;
    }

    /**
     * Cache-busting value for Glide.
     *
     * <p>The picture is always written to the <i>same</i> path, and Glide keys its cache on
     * the path - without this, replacing a photo would keep showing the old one. The version
     * is stamped at save time and passed to {@code RequestBuilder.signature(...)}, which is
     * part of Glide's cache key.
     */
    public static long avatarVersion(@NonNull Context c) {
        return prefs(c).getLong(KEY_VERSION, 0L);
    }

    /**
     * Copies {@code source} into app storage and stamps a new version, then runs
     * {@code onFinished} on the main thread.
     *
     * <p>The copy happens on a background thread: it is a few megabytes of I/O on a stream
     * backed by another process, which is not something to do on the main thread.
     * {@code onFinished} may arrive after the calling screen is gone, so it must not assume
     * a live view - check {@code isAdded()} before touching one.
     */
    public static void saveAvatar(@NonNull Context c,
                                 @NonNull Uri source,
                                 @Nullable Runnable onFinished) {
        final Context app = c.getApplicationContext();

        new Thread(() -> {
            boolean stored = copyInto(app, source);
            if (stored) {
                prefs(app).edit().putLong(KEY_VERSION, System.currentTimeMillis()).apply();
            }
            if (onFinished != null) {
                new Handler(Looper.getMainLooper()).post(onFinished);
            }
        }, "profile-avatar-save").start();
    }

    /**
     * Streams the picked image into place.
     *
     * <p>Writes to a temporary file and renames over the old one, so a failure part-way
     * through (the process dying, a revoked grant) cannot leave a half-written JPEG where
     * the avatar used to be - the previous picture survives instead.
     */
    private static boolean copyInto(@NonNull Context app, @NonNull Uri source) {
        File dir = app.getFilesDir();
        File temp = new File(dir, AVATAR_TEMP);
        File target = new File(dir, AVATAR_NAME);

        try (InputStream in = app.getContentResolver().openInputStream(source)) {
            if (in == null) {
                Log.w(TAG, "Picker returned no stream for " + source);
                return false;
            }
            try (OutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (IOException | SecurityException e) {
            Log.w(TAG, "Could not read the picked picture", e);
            deleteQuietly(temp);
            return false;
        }

        deleteQuietly(target);
        if (!temp.renameTo(target)) {
            Log.w(TAG, "Could not move the picked picture into place");
            deleteQuietly(temp);
            return false;
        }
        return true;
    }

    /** Deletes the picture, falling the top bar back to the person glyph. */
    public static void clearAvatar(@NonNull Context c) {
        deleteQuietly(avatarFileUnchecked(c));
        prefs(c).edit().remove(KEY_VERSION).apply();
    }

    private static void deleteQuietly(@NonNull File file) {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Could not delete " + file.getName());
        }
    }
}
