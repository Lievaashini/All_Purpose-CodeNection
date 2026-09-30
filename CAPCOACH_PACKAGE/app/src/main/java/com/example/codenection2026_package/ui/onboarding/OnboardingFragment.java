package com.example.codenection2026_package.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.api.HealthConnectManager;
import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.shell.ToneCopy;
import com.google.android.material.card.MaterialCardView;


/**
 * SCREEN 1 - Onboarding. Port of {@code prototype/onboarding.html}.
 */
public class OnboardingFragment extends Fragment {

    private ToneType selectedTone = ToneType.HYPE;

    private MaterialCardView cardHype;
    private MaterialCardView cardChill;
    private MaterialCardView cardPlain;

    private TextView dinoDialogue;
    private View healthStatusDot;
    private TextView healthStatusText;
    private boolean healthConnectGranted = false;

    // --- API INJECTIONS ---
    private HealthConnectManager healthManager;

    private final androidx.activity.result.ActivityResultLauncher<java.util.Set<String>> requestHealthPermissionLauncher =
            registerForActivityResult(
                    androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
                    grantedPermissions -> {
                        // Kotlin Interop Fix: Convert Java Class to Kotlin KClass for the API
                        kotlin.reflect.KClass<androidx.health.connect.client.records.SleepSessionRecord> sleepClass =
                                kotlin.jvm.JvmClassMappingKt.getKotlinClass(androidx.health.connect.client.records.SleepSessionRecord.class);

                        // Check if the user granted the baseline Sleep Read permission
                        if (grantedPermissions != null &&
                                grantedPermissions.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(sleepClass))) {
                            // 1. Update UI visually
                            setHealthConnectState(true);
                            android.util.Log.d("CapCoachAPI", "1. Sleep Permissions Granted via UI!");

                            // 2. Run your Automated Read/Write Database Test
                            try {
                                androidx.health.connect.client.HealthConnectClient client =
                                        androidx.health.connect.client.HealthConnectClient.getOrCreate(requireContext());

                                // Write the mock 4-hour sleep data
                                androidx.health.connect.client.records.SleepSessionRecord mockSleep = healthManager.createMockBurnoutSleep();
                                com.example.codenection2026_package.api.HealthConnectHelper.writeSleepDataSync(
                                        client, java.util.Collections.singletonList(mockSleep));
                                android.util.Log.d("CapCoachAPI", "2. Mock 4-hour sleep successfully written!");

                                // Read the data back
                                java.time.Instant start = java.time.Instant.parse("2026-09-23T00:00:00.000Z");
                                java.time.Instant end = java.time.Instant.parse("2026-09-24T23:59:59.000Z");
                                java.util.List<androidx.health.connect.client.records.SleepSessionRecord> records =
                                        com.example.codenection2026_package.api.HealthConnectHelper.readSleepDataSync(client, start, end);

                                android.util.Log.d("CapCoachAPI", "3. SUCCESS! Read " + records.size() + " sleep record(s) from Health Connect.");

                            } catch (Exception e) {
                                android.util.Log.e("CapCoachAPI", "Health Test failed: " + e.getMessage());
                            }
                        }
                    }
            );

    // -------------------------------

    public OnboardingFragment() {
        super(R.layout.fragment_onboarding);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // CRITICAL FIX: Initialize views FIRST before initializing managers/listeners that reference them
        dinoDialogue = view.findViewById(R.id.dinoDialogue);
        healthStatusDot = view.findViewById(R.id.healthStatusDot);
        healthStatusText = view.findViewById(R.id.healthStatusText);

        // --- Tone-aware prose ---
        // The layout carries the Hype wording so the preview and a cold first run look
        // right; this pass swaps in the stored tone's wording.
        bindToneCopy(view);

        // --- Hero Dino sprite ---
        // setImageResource() would freeze an animated GIF on its first frame, so the
        // sprite goes through Glide instead. The layout keeps android:src purely as a
        // static placeholder so the Android Studio preview pane still renders.
        ImageView dinoAvatar = view.findViewById(R.id.dinoAvatar);
        if (dinoAvatar != null) {
            Glide.with(this)
                    .load(R.drawable.dino_happy)
                    .into(dinoAvatar);
        }

        // --- Theme toggle (same helper is used on Screen 2) ---
        healthManager = new HealthConnectManager(requireContext());

        // --- Theme toggle ---
        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);

        // --- Tone cards ---
        cardHype = view.findViewById(R.id.toneCardHype);
        cardChill = view.findViewById(R.id.toneCardChill);
        cardPlain = view.findViewById(R.id.toneCardPlain);

        bindToneCard(cardHype, ToneType.HYPE);
        bindToneCard(cardChill, ToneType.CHILL);
        bindToneCard(cardPlain, ToneType.PLAIN);

        // Opens on the tone already stored rather than always on Hype, so a user who picked
        // Chill in Settings finds Chill selected here. On a first run nothing is stored yet
        // and this falls back to Hype, which is the prototype's default.
        selectTone(OnboardingPrefs.getTone(requireContext()), false);

        // --- Health Connect card ---
        View healthCard = view.findViewById(R.id.healthConnectCard);
        if (healthCard != null) {
            healthCard.setOnClickListener(v -> {
                if (healthManager.isClientAvailable()) {
                    requestHealthPermissionLauncher.launch(healthManager.getRequiredPermissions());
                } else {
                    toast("Health Connect is not installed on this device.");
                }
            });
        }

        // --- Mic button ---
        View.OnClickListener micTrigger = v -> {
            if (isAdded()) {
                com.example.codenection2026_package.ui.companion.VoiceDinoDialogFragment dialog =
                        new com.example.codenection2026_package.ui.companion.VoiceDinoDialogFragment();

                // Receive the text from the dialog and update the Onboarding screen
                dialog.setOnVoiceResultListener((raw, clean) -> {
                    if (dinoDialogue != null) {
                        // Was a hard-coded English prefix in Java. Extracted so it is
                        // translatable; it stays tone-neutral because it is the app echoing
                        // the user's own words back, not the Dino speaking.
                        dinoDialogue.setText(getString(R.string.dino_you_said, clean));
                    }
                });

                dialog.show(getParentFragmentManager(), "voice_dino");
            }
        };

        View micButton = view.findViewById(R.id.micButton);
        if (micButton != null) micButton.setOnClickListener(micTrigger);

        View talkButton = view.findViewById(R.id.talkToMeButton);
        if (talkButton != null) talkButton.setOnClickListener(micTrigger);

        // --- Continue to Screen 2 ---
        View continueButton = view.findViewById(R.id.continueButton);
        if (continueButton != null) {
            continueButton.setOnClickListener(v -> {
                if (isAdded()) {
                    getParentFragmentManager()
                            .beginTransaction()
                            .replace(R.id.nav_host_container, new HardLimitsFragment())
                            .addToBackStack(null)
                            .commit();
                }
            });
        }
    }

    /**
     * Rewrites this screen's tone-aware prose in the user's coaching tone.
     *
     * <p>Read once, at creation: the tone cards further down change the <i>stored</i>
     * preference, and the wording they select shows up the next time this screen or Settings
     * is opened. The Dino's greeting is the only line that re-words on the tap itself.
     */
    private void bindToneCopy(@NonNull View view) {
        if (getContext() == null) {
            return;
        }
        ToneCopy.on(view, OnboardingPrefs.getTone(requireContext()))
                .set(R.id.onboardingSubtitle, CoachVoice.Line.ONBOARD_SUBTITLE)
                .set(R.id.onboardingFootnote, CoachVoice.Line.ONBOARD_FOOTNOTE)
                .set(R.id.toneSectionLabel, CoachVoice.Line.TONE_SECTION_LABEL);
    }

    private void bindToneCard(@Nullable MaterialCardView card, @NonNull ToneType tone) {
        if (card == null) {
            return;
        }
        card.setChecked(tone == selectedTone);
        card.setOnClickListener(v -> selectTone(tone, true));
    }

    private void selectTone(@NonNull ToneType tone, boolean animateDialogue) {
        selectedTone = tone;

        // Persisted here so the choice survives this screen: OnboardingPrefs.saveTone was
        // written for exactly this call and had no caller, which is why a tone picked on
        // this screen never reached Settings. Writing during the restore above too is
        // harmless - it stores the value that was just read.
        OnboardingPrefs.saveTone(requireContext(), tone);

        // Re-word the rest of the screen straight away. Picking a tone IS the interaction on
        // this screen, so leaving the title, section label and footnote in the previous voice
        // until the screen is reopened would read as the choice not having taken effect.
        // Harmless on the initial restore, which simply sets the tone it just read.
        if (getView() != null) {
            bindToneCopy(getView());
        }

        applyToneCard(cardHype, tone == ToneType.HYPE, R.color.brand_mint);
        applyToneCard(cardChill, tone == ToneType.CHILL, R.color.secondary_blue);
        applyToneCard(cardPlain, tone == ToneType.PLAIN, R.color.tertiary_gold_container);

        if (dinoDialogue != null) {
            dinoDialogue.setText(
                    com.example.codenection2026_package.model.CoachVoice.Line.DINO_ONBOARDING.pick(tone));
            if (animateDialogue) {
                dinoDialogue.setAlpha(0f);
                dinoDialogue.animate().alpha(1f).setDuration(180L).start();
            }
        }
    }

    private void applyToneCard(@Nullable MaterialCardView card,
                               boolean checked,
                               int accentColorRes) {
        if (card == null) {
            return;
        }
        card.setChecked(checked);

        View indicator = card.findViewById(R.id.toneIndicator);
        ImageView indicatorIcon = card.findViewById(R.id.toneIndicatorIcon);
        ImageView toneIcon = card.findViewById(R.id.toneIcon);

        if (indicator != null) {
            indicator.setBackgroundResource(checked
                    ? R.drawable.bg_circle_mint
                    : R.drawable.bg_circle_indicator_off);
        }
        if (indicatorIcon != null) {
            indicatorIcon.setVisibility(checked ? View.VISIBLE : View.GONE);
        }
        if (toneIcon != null) {
            toneIcon.setImageTintList(
                    ContextCompat.getColorStateList(requireContext(), accentColorRes));
        }
    }

    @SuppressWarnings("unused")
    @NonNull
    public ToneType getSelectedTone() {
        return selectedTone;
    }

    public void setHealthConnectState(boolean granted) {
        healthConnectGranted = granted;
        if (healthStatusText != null) {
            healthStatusText.setText(granted
                    ? R.string.health_connect_status_ready
                    : R.string.health_connect_status_link);
            healthStatusText.setTextColor(ContextCompat.getColor(requireContext(),
                    granted ? R.color.brand_mint : R.color.text_dim_dark));
        }
        if (healthStatusDot != null) {
            healthStatusDot.setBackgroundResource(
                    granted ? R.drawable.dot_mint : R.drawable.dot_idle);
        }
    }

    @SuppressWarnings("unused")
    public boolean isHealthConnectGranted() {
        return healthConnectGranted;
    }

    private void toast(String message) {
        if (isAdded() && getContext() != null) {
            android.widget.Toast.makeText(getContext(), message,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}