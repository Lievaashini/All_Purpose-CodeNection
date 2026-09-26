package com.example.codenection2026_package.ui.onboarding;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.api.CalendarManager;
import com.example.codenection2026_package.ui.widget.ObservableScrollView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;

/**
 * SCREEN 2 - Set Limits. Port of {@code prototype/hard-limits.html}.
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

    private boolean initialized = false;

    // --- API INJECTIONS ---
    private CalendarManager calendarManager;

    private final androidx.activity.result.ActivityResultLauncher<String[]> requestCalendarPermissionsLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean readGranted = result.getOrDefault(Manifest.permission.READ_CALENDAR, false);
                Boolean writeGranted = result.getOrDefault(Manifest.permission.WRITE_CALENDAR, false);

                if (Boolean.TRUE.equals(readGranted) && Boolean.TRUE.equals(writeGranted)) {
                    runCalendarTest();
                } else {
                    Toast.makeText(getContext(), "Calendar access is required.", Toast.LENGTH_SHORT).show();
                }
            });
    // -------------------------------

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
        calendarManager = new CalendarManager(requireContext());

        bindViews(view);
        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);
        restoreSavedValues();
        wireSliders();
        wireScrollProgress(view);
        wireCalendarSync();
        wireSaveButton(view);
        wireThemePreview();

        initialized = true;
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
            if (initialized && getContext() != null && isChecked) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                    runCalendarTest();
                } else {
                    requestCalendarPermissionsLauncher.launch(new String[]{
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                    });
                }
            }
        });
    }

    /**
     * Seeds the Dino guard with the neutral sprite. {@link #refreshAll()} runs
     * immediately afterwards and overwrites it with the sprite for the restored load
     * band, so this only prevents a blank frame on first paint.
     */
    private void wireThemePreview(@NonNull View view) {
    private void runCalendarTest() {
        try {
            calendarManager.logUpcomingWeekEvents();
            calendarManager.blockRecoveryTime("Mandatory Brain Rest", 4);
            android.util.Log.d("CapCoachAPI", "Calendar automated test complete. Check Logcat.");
        } catch (Exception e) {
            android.util.Log.e("CapCoachAPI", "Calendar Test failed: " + e.getMessage());
        }
    }

    private void wireThemePreview() {
        if (dinoGuardAvatar != null) {
            loadDinoSprite(dinoGuardAvatar, R.drawable.dino_happy);
        }
    }

    /**
     * Loads a Dino sprite through Glide so an animated GIF actually plays.
     *
     * <p><b>Why not {@code setImageResource()}:</b> that decodes through
     * {@code BitmapDrawable}, which shows only the <i>first frame</i> of an animated
     * GIF. An animated sprite would silently sit still, looking like the animation
     * was never added. Glide decodes and plays the whole frame sequence.
     *
     * <p><b>Why not {@code asGif()}:</b> that forces the GIF decoder and <i>fails</i>
     * for any static PNG/WebP sprite. The default Drawable path animates a GIF when
     * the file is one, and renders a static frame when it is not - so the sprite set
     * can be swapped between formats without editing this method.
     */
    private void loadDinoSprite(@NonNull ImageView target, @DrawableRes int resId) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        Glide.with(this)
                .load(resId)
                .into(target);
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
        });
    }

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

        workHoursValue.setTextColor(accent);

        android.content.res.ColorStateList tintList = ContextCompat.getColorStateList(requireContext(), band.colorRes);
        if (tintList != null) {
            workSlider.setTrackActiveTintList(tintList);
            workSlider.setThumbTintList(tintList);
            workSlider.setHaloTintList(tintList);
        }

        if (workStatusLabel != null) {
            workStatusLabel.setText(band.statusLabelRes);
            workStatusLabel.setTextColor(accent);
        }
        if (workStatusDot != null) {
            workStatusDot.setBackgroundResource(dotFor(band));
        }

        if (workBadge != null) {
            workBadge.setText(work <= LoadZones.WORK_CAP_HOURS
                    ? getString(R.string.work_badge_prefix) + work + "h"
                    : (band == LoadZones.WorkBand.CAUTION ? "Caution: " : "High Risk: ") + work + "h");
            workBadge.setTextColor(accent);
        }

        if (workWarningText != null) {
            workWarningText.setText(band.warningTextRes);
        }
        if (workWarningIcon != null && tintList != null) {
            workWarningIcon.setImageTintList(tintList);
        }
        if (workWarningBox != null) {
            workWarningBox.setBackgroundResource(
                    band == LoadZones.WorkBand.SAFE
                            ? R.drawable.bg_surface_high
                            : R.drawable.bg_surface_low);
        }

        if (dinoGuardSpeech != null) {
            dinoGuardSpeech.setText(band == LoadZones.WorkBand.SAFE
                    ? getString(band.dinoSpeechRes, work)
                    : getString(band.dinoSpeechRes));
        }
        if (dinoGuardAvatar != null) {
            loadDinoSprite(dinoGuardAvatar, avatarFor(band));
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
            android.content.res.ColorStateList tintList = ContextCompat.getColorStateList(requireContext(), result.zone.colorRes);
            if (tintList != null) {
                totalCapacityIcon.setImageTintList(tintList);
            }
        }
        if (totalCapacityBanner != null) {
            android.graphics.drawable.Drawable bg = totalCapacityBanner.getBackground();
            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) bg.mutate())
                        .setStroke(dp1(), accent);
            }
        }
    }

    private int color(@Nullable View anchor, @ColorRes int colorRes) {
        Context ctx = anchor != null ? anchor.getContext() : getContext();
        if (ctx == null) {
            ctx = requireContext();
        }
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

    private int dp1() {
        return Math.round(getResources().getDisplayMetrics().density);
    }
}