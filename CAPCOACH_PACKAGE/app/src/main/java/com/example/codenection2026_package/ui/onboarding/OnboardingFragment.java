package com.example.codenection2026_package.ui.onboarding;

import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.codenection2026_package.R;
import com.example.codenection2026_package.api.HealthConnectManager;
import com.example.codenection2026_package.api.VoiceManager;
import com.example.codenection2026_package.model.ToneType;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Set;

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
    private VoiceManager voiceManager;
    private HealthConnectManager healthManager;

    private final androidx.activity.result.ActivityResultLauncher<java.util.Set<String>> requestHealthPermissionLauncher =
            registerForActivityResult(
                    androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
                    grantedPermissions -> {
                        if (grantedPermissions != null && healthManager != null && grantedPermissions.containsAll(healthManager.getRequiredPermissions())) {

                            // 1. Update your UI teammate's card visually
                            setHealthConnectState(true);
                            android.util.Log.d("CapCoachAPI", "1. Health Connect Permissions Granted via UI!");

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

    private final androidx.activity.result.ActivityResultLauncher<String> requestMicPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                if (Boolean.TRUE.equals(isGranted) && voiceManager != null) {
                    voiceManager.startListening();
                }
            });
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

        healthManager = new HealthConnectManager(requireContext());

        voiceManager = new VoiceManager(requireContext(), new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                if (dinoDialogue != null) dinoDialogue.setText("Listening...");
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                if (dinoDialogue != null) dinoDialogue.setText("I didn't quite catch that.");
            }
            @Override public void onResults(Bundle results) {
                if (results != null) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty() && dinoDialogue != null) {
                        String spokenText = matches.get(0);
                        dinoDialogue.setText("You said: " + spokenText);

                        // --- WEEK 2 ML BINDING TEST ---
                        // --- WEEK 2 RAW TFLITE TEST ---
                        try {
                            // 1. Load the raw model file from the assets folder
                            android.content.res.AssetFileDescriptor fileDescriptor = requireContext().getAssets().openFd("text_classification_v2.tflite");
                            java.io.FileInputStream inputStream = new java.io.FileInputStream(fileDescriptor.getFileDescriptor());
                            java.nio.channels.FileChannel fileChannel = inputStream.getChannel();
                            java.nio.MappedByteBuffer modelBuffer = fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());

                            // 2. Initialize the core TFLite Engine
                            org.tensorflow.lite.Interpreter tflite = new org.tensorflow.lite.Interpreter(modelBuffer);

                            // 3. Prepare inputs and outputs using raw Java arrays
                            int[][] input = new int[1][256]; // Dummy 256 integer tokens
                            float[][] output = new float[1][2]; // Assuming the model outputs 2 probability categories

                            // 4. Run Inference
                            tflite.run(input, output);

                            android.util.Log.d("CapCoachAPI", "Raw TFLite Pipeline Connected! Output: " + output[0][0]);

                            // 5. Prevent memory leaks
                            tflite.close();
                            fileChannel.close();
                            inputStream.close();
                        } catch (Exception e) {
                            android.util.Log.e("CapCoachAPI", "TFLite failed: " + e.getMessage());
                        }
                    }
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        // --- Theme toggle ---
        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);

        // --- Tone cards ---
        cardHype = view.findViewById(R.id.toneCardHype);
        cardChill = view.findViewById(R.id.toneCardChill);
        cardPlain = view.findViewById(R.id.toneCardPlain);

        bindToneCard(cardHype, ToneType.HYPE);
        bindToneCard(cardChill, ToneType.CHILL);
        bindToneCard(cardPlain, ToneType.PLAIN);

        selectTone(ToneType.HYPE, false);

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
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (voiceManager != null) voiceManager.startListening();
            } else {
                requestMicPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO);
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

    private void bindToneCard(@Nullable MaterialCardView card, @NonNull ToneType tone) {
        if (card == null) {
            return;
        }
        card.setChecked(tone == selectedTone);
        card.setOnClickListener(v -> selectTone(tone, true));
    }

    private void selectTone(@NonNull ToneType tone, boolean animateDialogue) {
        selectedTone = tone;

        applyToneCard(cardHype, tone == ToneType.HYPE, R.color.brand_mint);
        applyToneCard(cardChill, tone == ToneType.CHILL, R.color.secondary_blue);
        applyToneCard(cardPlain, tone == ToneType.PLAIN, R.color.tertiary_gold_container);

        if (dinoDialogue != null) {
            dinoDialogue.setText(tone.speechRes);
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
        if (voiceManager != null) voiceManager.destroy();
    }
}