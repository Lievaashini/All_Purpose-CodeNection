package com.example.codenection2026_package.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.codenection2026_package.R;
import com.example.codenection2026_package.ui.widget.ObservableScrollView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;

/**
 * SCREEN 2 - Set Limits. Port of {@code prototype/hard-limits.html}.
 *
 * <p>Three sliders define the weekly baseline:
 * study (10-50), work (0-40), co-curricular (0-20). Their sum is graded live by
 * {@link LoadZones} and reflected in the banner, the work-status pill, the warning
 * callout, and the Dino's dialogue.
 *
 * <p>This replaces the prototype's {@code updateWorkSliderState()}, which referenced
 * DOM ids ({@code statusPill}, {@code warningBox}) that no longer existed in the HTML
 * and therefore threw on every drag.
 */
public class HardLimitsFragment extends Fragment {

    private Slider studySlider;
    private Slider workSlider;
    private Slider cocurricularSlider;

    private TextView studyHoursValue;
    private TextView workHoursValue;
    private TextView cocurricularHoursValue;

    private TextView totalHoursValue;
    private TextView zoneLabel;
    private View zoneDot;
    private View totalCapacityBanner;
    private ImageView totalCapacityIcon;

    private TextView workStatusLabel;
    private View workStatusDot;
    private TextView workBadge;
    private View workWarningBox;
    private ImageView workWarningIcon;
    private TextView workWarningText;
    private TextView dinoGuardSpeech;
    private ImageView dinoGuardAvatar;
    private View dinoStatusDot;

    private MaterialSwitch calendarSyncSwitch;

    private boolean initialised = false;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hard_limits, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);
        restoreSavedValues();
        wireSliders();
        wireScrollProgress(view);
        wireCalendarSync();
        wireSaveButton(view);
        wireThemePreview(view);

        // Render once with the restored values.
        initialised = true;
        refreshAll();
    }

    private void bindViews(@NonNull View view) {
        studySlider = view.findViewById(R.id.studySlider);
        workSlider = view.findViewById(R.id.workSlider);
        cocurricularSlider = view.findViewById(R.id.cocurricularSlider);

        studyHoursValue = view.findViewById(R.id.studyHoursValue);
        workHoursValue = view.findViewById(R.id.workHoursValue);
        cocurricularHoursValue = view.findViewById(R.id.cocurricularHoursValue);

        totalHoursValue = view.findViewById(R.id.totalHoursValue);
        zoneLabel = view.findViewById(R.id.zoneLabel);
        zoneDot = view.findViewById(R.id.zoneDot);
        totalCapacityBanner = view.findViewById(R.id.totalCapacityBanner);
        totalCapacityIcon = view.findViewById(R.id.totalCapacityIcon);

        workStatusLabel = view.findViewById(R.id.workStatusLabel);
        workStatusDot = view.findViewById(R.id.workStatusDot);
        workBadge = view.findViewById(R.id.workBadge);
        workWarningBox = view.findViewById(R.id.workWarningBox);
        workWarningIcon = view.findViewById(R.id.workWarningIcon);
        workWarningText = view.findViewById(R.id.workWarningText);

        dinoGuardSpeech = view.findViewById(R.id.dinoGuardSpeech);
        dinoGuardAvatar = view.findViewById(R.id.dinoGuardAvatar);
        dinoStatusDot = view.findViewById(R.id.dinoStatusDot);

        calendarSyncSwitch = view.findViewById(R.id.calendarSyncSwitch);
    }

    private void restoreSavedValues() {
        if (getContext() == null) {
            return;
        }
        studySlider.setValue(OnboardingPrefs.getStudyHours(requireContext()));
        workSlider.setValue(OnboardingPrefs.getWorkHours(requireContext()));
        cocurricularSlider.setValue(OnboardingPrefs.getCocurricularHours(requireContext()));
        if (calendarSyncSwitch != null) {
            calendarSyncSwitch.setChecked(OnboardingPrefs.isCalendarSynced(requireContext()));
        }
    }

    private void wireSliders() {
        Slider.OnChangeListener listener = (slider, value, fromUser) -> refreshAll();
        studySlider.addOnChangeListener(listener);
        workSlider.addOnChangeListener(listener);
        cocurricularSlider.addOnChangeListener(listener);
    }

    /**
     * Thin progress bar at the top, mirroring the prototype's scroll indicator.
     *
     * <p>There is no {@code ViewCompat.setOnScrollChangeListener} - that API does not
     * exist. {@link ObservableScrollView} overrides {@code onScrollChanged} instead,
     * which is stable across all API levels.
     */
    private void wireScrollProgress(@NonNull View view) {
        final LinearProgressIndicator progress = view.findViewById(R.id.scrollProgress);
        View scroll = view.findViewById(R.id.limitsScroll);

        if (progress == null || !(scroll instanceof ObservableScrollView)) {
            return;
        }

        ((ObservableScrollView) scroll).setOnScrollProgressListener(
                percent -> progress.setProgressCompat(percent, true));
    }
    private void wireCalendarSync() {
        if (calendarSyncSwitch == null) {
            return;
        }
        calendarSyncSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (initialised && getContext() != null) {
                Toast.makeText(requireContext(),
                        isChecked ? "Calendar sync enabled" : "Calendar sync disabled",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void wireThemePreview(@NonNull View view) {
        // Keeps the Dino vector tinted correctly when the palette flips.
        if (dinoGuardAvatar != null) {
            dinoGuardAvatar.setImageResource(R.drawable.dino_happy);
        }
    }

    private void wireSaveButton(@NonNull View view) {
        MaterialButton save = view.findViewById(R.id.saveAndLaunchButton);
        if (save == null) {
            return;
        }
        save.setOnClickListener(v -> {
            if (getContext() == null) {
                return;
            }
            OnboardingPrefs.save(
                    requireContext(),
                    (int) studySlider.getValue(),
                    (int) workSlider.getValue(),
                    (int) cocurricularSlider.getValue(),
                    calendarSyncSwitch != null && calendarSyncSwitch.isChecked());

            Toast.makeText(requireContext(),
                    "Baseline saved", Toast.LENGTH_SHORT).show();

            // Screen 2 is the end of the Week 1 flow. Role 1 routes to the Dashboard here.
        });
    }

    // ==================================================================
    //  Rendering
    // ==================================================================

    private void refreshAll() {
        int study = (int) studySlider.getValue();
        int work = (int) workSlider.getValue();
        int cocurricular = (int) cocurricularSlider.getValue();

        studyHoursValue.setText(String.valueOf(study));
        workHoursValue.setText(String.valueOf(work));
        cocurricularHoursValue.setText(String.valueOf(cocurricular));

        renderStudyAccent(study);
        renderCocurricularAccent(cocurricular);
        renderWork(work);
        renderTotal(study, work, cocurricular);
    }

    private void renderStudyAccent(int study) {
        int color = study > LoadZones.STUDY_CAUTION_HOURS
                ? R.color.status_amber
                : R.color.brand_mint;
        studyHoursValue.setTextColor(color(studyHoursValue, color));
    }

    private void renderCocurricularAccent(int cocurricular) {
        int color = cocurricular > LoadZones.COCURRICULAR_CAUTION_HOURS
                ? R.color.status_amber
                : R.color.brand_mint;
        cocurricularHoursValue.setTextColor(color(cocurricularHoursValue, color));
    }

    private void renderWork(int work) {
        LoadZones.WorkBand band = LoadZones.workBand(work);
        int accent = color(workHoursValue, band.colorRes);

        // Big number colour
        workHoursValue.setTextColor(accent);

        // Slider track + thumb follow the same colour so the control matches the warning.
        workSlider.setTrackActiveTintList(
                ContextCompat.getColorStateList(requireContext(), band.colorRes));
        workSlider.setThumbTintList(
                ContextCompat.getColorStateList(requireContext(), band.colorRes));
        workSlider.setHaloTintList(
                ContextCompat.getColorStateList(requireContext(), band.colorRes));

        // Status pill
        if (workStatusLabel != null) {
            workStatusLabel.setText(band.statusLabelRes);
            workStatusLabel.setTextColor(accent);
        }
        if (workStatusDot != null) {
            workStatusDot.setBackgroundResource(dotFor(band));
        }

        // Protected badge
        if (workBadge != null) {
            workBadge.setText(work <= LoadZones.WORK_CAP_HOURS
                    ? getString(R.string.work_badge_prefix) + work + "h"
                    : (band == LoadZones.WorkBand.CAUTION ? "Caution: " : "High Risk: ") + work + "h");
            workBadge.setTextColor(accent);
        }

        // Warning callout
        if (workWarningText != null) {
            workWarningText.setText(band.warningTextRes);
        }
        if (workWarningIcon != null) {
            workWarningIcon.setImageTintList(
                    ContextCompat.getColorStateList(requireContext(), band.colorRes));
        }
        if (workWarningBox != null) {
            workWarningBox.setBackgroundResource(
                    band == LoadZones.WorkBand.SAFE
                            ? R.drawable.bg_surface_high
                            : R.drawable.bg_surface_low);
        }

        // Dino speech + avatar + status dot
        if (dinoGuardSpeech != null) {
            dinoGuardSpeech.setText(band == LoadZones.WorkBand.SAFE
                    ? getString(band.dinoSpeechRes, work)
                    : getString(band.dinoSpeechRes));
        }
        if (dinoGuardAvatar != null) {
            dinoGuardAvatar.setImageResource(avatarFor(band));
        }
        if (dinoStatusDot != null) {
            dinoStatusDot.setBackgroundResource(dotFor(band));
        }
    }

    private void renderTotal(int study, int work, int cocurricular) {
        LoadZones.Result result = LoadZones.grade(study, work, cocurricular);
        int accent = color(zoneLabel, result.zone.colorRes);

        if (totalHoursValue != null) {
            totalHoursValue.setText(String.valueOf(result.totalHours));
            totalHoursValue.setTextColor(accent);
        }
        if (zoneLabel != null) {
            zoneLabel.setText(result.zone.labelRes);
            zoneLabel.setTextColor(accent);
        }
        if (zoneDot != null) {
            zoneDot.setBackgroundResource(dotFor(result.zone));
        }
        if (totalCapacityIcon != null) {
            totalCapacityIcon.setImageTintList(
                    ContextCompat.getColorStateList(requireContext(), result.zone.colorRes));
        }
        if (totalCapacityBanner != null) {
            // GradientDrawable lets us recolour the 1dp border per zone.
            android.graphics.drawable.Drawable bg = totalCapacityBanner.getBackground();
            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) bg.mutate())
                        .setStroke(dp(1), accent);
            }
        }
    }

    // ==================================================================
    //  Small helpers
    // ==================================================================

    private int color(@Nullable View anchor, @ColorRes int colorRes) {
        android.content.Context ctx = anchor != null ? anchor.getContext() : getContext();
        return ContextCompat.getColor(ctx, colorRes);
    }

    @androidx.annotation.DrawableRes
    private static int dotFor(LoadZones.WorkBand band) {
        switch (band) {
            case CAUTION:
                return R.drawable.dot_amber;
            case RISK:
                return R.drawable.dot_red;
            case SAFE:
            default:
                return R.drawable.dot_mint;
        }
    }

    @androidx.annotation.DrawableRes
    private static int dotFor(LoadZones.Zone zone) {
        switch (zone) {
            case ELEVATED:
                return R.drawable.dot_amber;
            case OVERLOAD:
                return R.drawable.dot_red;
            case SAFE:
            default:
                return R.drawable.dot_mint;
        }
    }

    /**
     * Maps the work-hour band to the Dino sprite.
     *
     * <p>Uses the real prototype art:
     * <ul>
     *   <li>{@code dino_happy}    - thriving_big - green, healthy</li>
     *   <li>{@code dino_steady}   - steady_big   - amber, holding on</li>
     *   <li>{@code dino_overload} - overload_big - red, strained</li>
     * </ul>
     *
     * <p>{@code dino_dead} (dead_big) is reserved for a fully burnt-out state - it is
     * available but not wired to a band yet, because the prototype's work slider only
     * had three thresholds.
     */
    @androidx.annotation.DrawableRes
    private static int avatarFor(LoadZones.WorkBand band) {
        switch (band) {
            case CAUTION:
                return R.drawable.dino_steady;
            case RISK:
                return R.drawable.dino_overload;
            case SAFE:
            default:
                return R.drawable.dino_happy;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
