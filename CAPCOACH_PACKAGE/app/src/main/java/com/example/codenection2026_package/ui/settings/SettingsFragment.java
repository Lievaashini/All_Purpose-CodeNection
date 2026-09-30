package com.example.codenection2026_package.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.example.codenection2026_package.ui.profile.ProfileStore;
import com.example.codenection2026_package.ui.shell.AppHeader;
import com.example.codenection2026_package.ui.shell.ScreenNav;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;

/**
 * SCREEN 8 - SETTINGS (port of Prototype/settings.html).
 *
 * <p>The prototype flips themes by rewriting tailwind.config at runtime and leaves the
 * institutional toggle purely decorative. Here the theme switch drives
 * {@link AppCompatDelegate} (through the shared {@link ThemeController}) and the telemetry
 * switch persists a real preference that repaints the academic link status.
 *
 * <p>Profile and Coach Tone were added afterwards. Both write through
 * {@link OnboardingPrefs}: the name here is the same value Screen 2 collects, and the tone
 * is the value Screen 1 collects, so no screen owns a private copy of either.
 */
public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "capcoach_settings";
    private static final String KEY_TELEMETRY_SHARE = "telemetry_share";

    private View themeNightButton;
    private View themeBrightButton;
    private TextView themeNightLabel;
    private TextView themeBrightLabel;

    private View linkStatusPill;
    private View linkStatusDot;
    private TextView linkStatusText;

    private MaterialSwitch telemetrySwitch;

    private ImageView avatarPreviewPhoto;
    private ImageView avatarPreviewGlyph;
    private MaterialButton changePhotoButton;
    private MaterialButton removePhotoButton;
    private EditText nameInput;

    private ToneSelector toneSelector;

    /** Guards the checked change listener while the saved value is being restored. */
    private boolean restoringTelemetry;

    /**
     * Android's system photo picker.
     *
     * <p>Chosen over {@code ACTION_GET_CONTENT} because it requires no permission at any API
     * level: on API 33+ it is part of the OS and returns only the one item the user tapped,
     * and below that androidx falls back to the Storage Access Framework, which is also
     * grant-per-URI and permission-free. {@code PickVisualMedia} needs androidx.activity 1.7+
     * and this project is on 1.8.0.
     */
    private final ActivityResultLauncher<PickVisualMediaRequest> avatarPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null || !isAdded()) {
                    // Null means the user backed out of the picker; nothing to do.
                    return;
                }
                ProfileStore.saveAvatar(requireContext(), uri, this::renderProfile);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ScreenNav.bindNav(this, view, ScreenNav.Tab.SETTINGS);

        // The shared top bar. AppHeader starts the header mascot's GIF too, so this screen
        // no longer loads it itself - the header is now identical to Dashboard's and
        // Biometrics', including the Offline ML pill that used to sit in it being gone.
        AppHeader.bind(this, view, R.string.settings_title);

        themeNightButton = view.findViewById(R.id.themeNightButton);
        themeBrightButton = view.findViewById(R.id.themeBrightButton);
        themeNightLabel = view.findViewById(R.id.themeNightLabel);
        themeBrightLabel = view.findViewById(R.id.themeBrightLabel);
        linkStatusPill = view.findViewById(R.id.linkStatusPill);
        linkStatusDot = view.findViewById(R.id.linkStatusDot);
        linkStatusText = view.findViewById(R.id.linkStatusText);
        telemetrySwitch = view.findViewById(R.id.telemetrySwitch);

        avatarPreviewPhoto = view.findViewById(R.id.avatarPreviewPhoto);
        avatarPreviewGlyph = view.findViewById(R.id.avatarPreviewGlyph);
        changePhotoButton = view.findViewById(R.id.changePhotoButton);
        removePhotoButton = view.findViewById(R.id.removePhotoButton);
        nameInput = view.findViewById(R.id.settingsNameInput);

        themeNightButton.setOnClickListener(v -> {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            refreshThemeSegment();
        });
        themeBrightButton.setOnClickListener(v -> {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            refreshThemeSegment();
        });
        refreshThemeSegment();
        bindTelemetrySwitch();
        bindProfile();
        toneSelector = new ToneSelector(this, view);
        toneSelector.bind();
    }

    @Override
    public void onResume() {
        super.onResume();
        // The header toggle can flip the mode while this screen stays alive, so the
        // segmented control is repainted every time the screen comes back to the front.
        refreshThemeSegment();
    }

    // ==================================================================
    // Theme segmented control
    // ==================================================================

    /** Paints the selected segment mint and the other one muted, mirroring the prototype. */
    private void refreshThemeSegment() {
        if (themeNightButton == null
                || themeBrightButton == null
                || themeNightLabel == null
                || themeBrightLabel == null
                || !isAdded()) {
            return;
        }
        boolean night = ThemeController.isNightMode(requireContext());
        paintSegment(themeNightButton, themeNightLabel, night);
        paintSegment(themeBrightButton, themeBrightLabel, !night);
    }

    private void paintSegment(@NonNull View button,
                              @NonNull TextView label,
                              boolean selected) {
        @DrawableRes int background = selected
                ? R.drawable.bg_segment_on
                : R.drawable.bg_surface_low;
        button.setBackgroundResource(background);
        label.setTextColor(ContextCompat.getColor(requireContext(),
                selected ? R.color.brand_mint : R.color.text_muted_dark));
    }

    // ==================================================================
    // Institutional telemetry switch
    // ==================================================================

    private void bindTelemetrySwitch() {
        if (telemetrySwitch == null) {
            return;
        }

        restoringTelemetry = true;
        telemetrySwitch.setChecked(prefs().getBoolean(KEY_TELEMETRY_SHARE, false));
        restoringTelemetry = false;

        applyTelemetryState(telemetrySwitch.isChecked());

        telemetrySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (restoringTelemetry) {
                return;
            }
            prefs().edit().putBoolean(KEY_TELEMETRY_SHARE, isChecked).apply();
            applyTelemetryState(isChecked);
        });
    }

    private void applyTelemetryState(boolean enabled) {
        if (linkStatusPill == null || linkStatusDot == null || linkStatusText == null) {
            return;
        }
        linkStatusPill.setBackgroundResource(enabled
                ? R.drawable.bg_tag_mint
                : R.drawable.bg_pill_dark);
        linkStatusDot.setBackgroundResource(enabled
                ? R.drawable.dot_mint
                : R.drawable.dot_idle);
        linkStatusText.setText(enabled
                ? R.string.settings_link_active
                : R.string.settings_link_disconnected);
        linkStatusText.setTextColor(ContextCompat.getColor(requireContext(),
                enabled ? R.color.brand_mint : R.color.text_muted_dark));
    }

    @NonNull
    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ==================================================================
    // Profile - photo and display name
    // ==================================================================

    private void bindProfile() {
        if (changePhotoButton != null) {
            changePhotoButton.setOnClickListener(v -> pickPhoto());
        }
        if (removePhotoButton != null) {
            removePhotoButton.setOnClickListener(v -> {
                ProfileStore.clearAvatar(requireContext());
                renderProfile();
            });
        }
        bindNameField();
        renderProfile();
    }

    private void pickPhoto() {
        avatarPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    /**
     * Repaints both the preview and the shared top bar.
     *
     * <p>The preview and the bar are separate ImageViews, so a change has to be pushed to
     * both. Called again from the background copy's completion callback, which is why it
     * re-checks that the screen is still alive before touching any view.
     */
    private void renderProfile() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        File file = ProfileStore.avatarFile(requireContext());

        if (avatarPreviewPhoto != null && avatarPreviewGlyph != null) {
            boolean hasPhoto = file != null;
            avatarPreviewPhoto.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
            avatarPreviewGlyph.setVisibility(hasPhoto ? View.GONE : View.VISIBLE);

            if (file != null) {
                Glide.with(this)
                        .load(file)
                        .signature(new ObjectKey(ProfileStore.avatarVersion(requireContext())))
                        .circleCrop()
                        .into(avatarPreviewPhoto);
            }
        }

        if (removePhotoButton != null) {
            // Gone rather than disabled: with no picture stored there is nothing to remove,
            // and an inert button next to the live one just invites a pointless tap.
            removePhotoButton.setVisibility(file != null ? View.VISIBLE : View.GONE);
        }

        View root = getView();
        if (root != null) {
            AppHeader.bindAvatar(this, root);
        }
    }

    /**
     * Wires the display-name field.
     *
     * <p>There is no Save button, matching the theme switch above - the value is committed
     * as soon as the field loses focus, and again from {@link #onPause()} and
     * {@link #onDestroyView()} so navigating away or backgrounding the app cannot drop an
     * edit.
     *
     * <p><b>Why not save on every keystroke:</b> doing that looked simpler, but backspacing
     * a name away saves each intermediate value on the way down, so clearing the field left
     * the first letter stored ("Wei Ling" -> ... -> "W"). Committing once, on the way out,
     * means an abandoned edit simply never lands: an empty field is discarded and the stored
     * name is put back, so what is shown and what is stored cannot disagree.
     */
    private void bindNameField() {
        if (nameInput == null || getContext() == null) {
            return;
        }
        nameInput.setText(OnboardingPrefs.getDisplayName(requireContext()));
        nameInput.setSelection(nameInput.getText().length());
        nameInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                commitName();
            }
        });
    }

    /** Commits the field, or restores the stored name when the user emptied it. */
    private void commitName() {
        if (nameInput == null || getContext() == null) {
            return;
        }
        String typed = nameInput.getText().toString().trim();
        if (!typed.isEmpty()) {
            OnboardingPrefs.saveName(requireContext(), typed);
            return;
        }
        nameInput.setText(OnboardingPrefs.getDisplayName(requireContext()));
        nameInput.setSelection(nameInput.getText().length());
    }

    @Override
    public void onPause() {
        super.onPause();
        // Backgrounding does not destroy the view, so the edit would otherwise sit in a
        // field the user may never come back to.
        commitName();
    }

    @Override
    public void onDestroyView() {
        // Last chance before the views go: covers leaving via the bottom nav, where the
        // blur may not have been reported.
        commitName();

        themeNightButton = null;
        themeBrightButton = null;
        themeNightLabel = null;
        themeBrightLabel = null;
        linkStatusPill = null;
        linkStatusDot = null;
        linkStatusText = null;
        telemetrySwitch = null;
        avatarPreviewPhoto = null;
        avatarPreviewGlyph = null;
        changePhotoButton = null;
        removePhotoButton = null;
        nameInput = null;
        toneSelector = null;

        super.onDestroyView();
    }
}
