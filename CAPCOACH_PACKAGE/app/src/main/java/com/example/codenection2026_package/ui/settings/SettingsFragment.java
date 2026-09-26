package com.example.codenection2026_package.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.codenection2026_package.R;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.example.codenection2026_package.ui.shell.ScreenNav;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * SCREEN 8 - SETTINGS (port of Prototype/settings.html).
 *
 * <p>The prototype flips themes by rewriting tailwind.config at runtime and leaves the
 * institutional toggle purely decorative. Here the theme switch drives
 * {@link AppCompatDelegate} (through the shared {@link ThemeController}) and the telemetry
 * switch persists a real preference that repaints the academic link status.
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

    /** Guards the checked change listener while the saved value is being restored. */
    private boolean restoringTelemetry;

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
        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);

        // The header logo is an animated GIF. The layout keeps android:src purely so the
        // Android Studio preview pane still renders something, but that path decodes only
        // the first frame - so the sprite is loaded through Glide here to actually animate.
        android.widget.ImageView headerDino = view.findViewById(R.id.headerDino);
        if (headerDino != null && isAdded()) {
            com.bumptech.glide.Glide.with(this)
                    .load(R.drawable.dino_happy)
                    .into(headerDino);
        }

        themeNightButton = view.findViewById(R.id.themeNightButton);
        themeBrightButton = view.findViewById(R.id.themeBrightButton);
        themeNightLabel = view.findViewById(R.id.themeNightLabel);
        themeBrightLabel = view.findViewById(R.id.themeBrightLabel);
        linkStatusPill = view.findViewById(R.id.linkStatusPill);
        linkStatusDot = view.findViewById(R.id.linkStatusDot);
        linkStatusText = view.findViewById(R.id.linkStatusText);
        telemetrySwitch = view.findViewById(R.id.telemetrySwitch);

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

    @Override
    public void onDestroyView() {
        themeNightButton = null;
        themeBrightButton = null;
        themeNightLabel = null;
        themeBrightLabel = null;
        linkStatusPill = null;
        linkStatusDot = null;
        linkStatusText = null;
        telemetrySwitch = null;

        super.onDestroyView();
    }
}
