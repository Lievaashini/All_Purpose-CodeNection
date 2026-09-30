package com.example.codenection2026_package.ui.shell;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.example.codenection2026_package.ui.profile.ProfileStore;

import java.io.File;

/**
 * Wires the shared top bar ({@code layout/include_app_header.xml}) for a bottom-nav screen.
 *
 * <p>The bar itself is <b>deliberately dumb</b>: every position and size that decides
 * where the mascot, the theme toggle and the profile picture land lives in the layout, so
 * the three screens cannot disagree about them. The only thing that genuinely differs per
 * screen is the subtitle under the CAPCOACH wordmark and the fact that the mascot is an
 * animated GIF, so this class supplies exactly those two and nothing else.
 *
 * <p>Call it once from {@code onViewCreated}:
 * <pre>
 *   ScreenNav.bindNav(this, view, ScreenNav.Tab.HOME);
 *   AppHeader.bind(this, view, R.string.brand_offline_ml);
 * </pre>
 *
 * <p><b>Why the subtitle is passed in rather than set in each layout:</b> {@code <include>}
 * can override a child's layout attributes but not its text, so three copies of the bar
 * would be back the moment each screen wanted its own title. Passing one string keeps a
 * single bar while still letting each screen name itself.
 */
public final class AppHeader {

    private AppHeader() {
    }

    /**
     * Fills in the bar found at {@code root}.
     *
     * <p>Every lookup is null-tolerant: the screens that do not host the bar (Daily
     * Harvest, Feast, the onboarding flow and the bottom sheets) are free to call this
     * without crashing, and an include that failed to inflate is a no-op rather than a
     * crash on a missing view.
     *
     * @param host        screen that owns the bar, used to start the Glide GIF load
     * @param root        any view in that screen
     * @param subtitleRes screen name shown under the CAPCOACH wordmark,
     *                    e.g. {@code R.string.settings_title}
     */
    public static void bind(@NonNull Fragment host,
                            @NonNull View root,
                            @StringRes int subtitleRes) {
        updateSubtitle(host, root, subtitleRes);
        loadMascot(host, root);
        bindAvatar(host, root);
        ThemeController.bind(root, R.id.themeToggleButton, R.id.themeToggleIcon);
    }

    /**
     * Paints the profile picture, falling back to the person glyph when there is none.
     *
     * <p>Called by {@link #bind}, and again by Settings after the user changes or removes
     * their photo so its own bar updates without a full re-bind.
     *
     * <p>The photo is loaded with {@code signature(...)} because every saved picture
     * overwrites the same file: Glide keys its cache on the path, so without a changing
     * signature the bar would keep showing the picture the user just replaced.
     */
    public static void bindAvatar(@NonNull Fragment host, @NonNull View root) {
        ImageView photo = root.findViewById(R.id.avatarPhoto);
        ImageView glyph = root.findViewById(R.id.avatarGlyph);
        if (photo == null || glyph == null || !host.isAdded()) {
            return;
        }

        File file = ProfileStore.avatarFile(host.requireContext());
        if (file == null) {
            photo.setVisibility(View.GONE);
            glyph.setVisibility(View.VISIBLE);
            return;
        }

        glyph.setVisibility(View.GONE);
        photo.setVisibility(View.VISIBLE);
        Glide.with(host)
                .load(file)
                .signature(new ObjectKey(ProfileStore.avatarVersion(host.requireContext())))
                .circleCrop()
                .into(photo);
    }

    /** Names the current screen in the bar. Blank text collapses the row instead of leaving a gap. */
    private static void updateSubtitle(@NonNull Fragment host,
                                      @NonNull View root,
                                      @StringRes int subtitleRes) {
        TextView subtitle = root.findViewById(R.id.headerSubtitle);
        if (subtitle == null) {
            return;
        }
        if (subtitleRes == 0) {
            subtitle.setVisibility(View.GONE);
            return;
        }
        subtitle.setVisibility(View.VISIBLE);
        subtitle.setText(host.getString(subtitleRes));
    }

    /**
     * Starts the header mascot's animation.
     *
     * <p>The asset is an animated GIF. {@code android:src} in the layout renders only its
     * first frame, so the sprite goes through Glide to actually play - the same treatment
     * {@code DashboardFragment} already gave the hero mascot.
     */
    private static void loadMascot(@NonNull Fragment host, @NonNull View root) {
        ImageView mascot = root.findViewById(R.id.headerDino);
        if (mascot == null || !host.isAdded()) {
            return;
        }
        Glide.with(host)
                .load(R.drawable.dino_happy)
                .into(mascot);
    }

    /**
     * @return the profile-picture plate, or null when the screen has no shared bar.
     *         Exposed so a screen can hang a click listener on it without hard-coding
     *         the id.
     */
    @Nullable
    public static View profilePicture(@NonNull View root) {
        return root.findViewById(R.id.avatarBadge);
    }
}
