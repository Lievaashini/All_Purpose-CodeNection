package com.example.codenection2026_package.ui.companion;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.api.VoiceManager;
import com.example.codenection2026_package.ui.addtask.AddTaskSheetFragment;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.example.codenection2026_package.ui.shell.ScreenNav;

import java.util.ArrayList;
import java.util.Locale;

/**
 * SCREEN 9 - COMPANION / VOICE. Port of Prototype/companion.html.
 *
 * <p><b>This screen reuses the team's VoiceManager.</b> The prototype drives the Web
 * Speech API from a page-local script; on Android that job already belongs to
 * api/VoiceManager.java, which wraps SpeechRecognizer and sets EXTRA_PREFER_OFFLINE for
 * the app's offline-first pitch. This fragment therefore does NOT create its own
 * recognizer - it hands VoiceManager a RecognitionListener and forwards the transcript
 * into ScreenNav, exactly as the prototype forwarded it to window.location.
 *
 * <p>The prototype's theme button toggles a local CSS class. The shared ThemeController
 * is used instead, so the night/bright choice stays consistent with every other screen.
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public class CompanionFragment extends Fragment {

    private TextView commandBubble;
    private TextView voiceStatus;
    private TextView readyText;
    private View micButton;

    /** The team's speech wrapper. Owned here, destroyed in onDestroyView. */
    private VoiceManager voiceManager;

    private ObjectAnimator pulseAnimator;

    /** Mirrors the prototype's browser permission prompt. */
    private final ActivityResultLauncher<String> requestMicPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (Boolean.TRUE.equals(isGranted)) {
                            startListening();
                        } else if (commandBubble != null) {
                            commandBubble.setText(R.string.voice_unavailable);
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_companion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        commandBubble = view.findViewById(R.id.commandBubble);
        voiceStatus = view.findViewById(R.id.voiceStatus);
        readyText = view.findViewById(R.id.readyText);
        micButton = view.findViewById(R.id.micButton);

        // Animated mascot: the asset is a GIF, so it has to go through Glide.
        ImageView dinoAvatar = view.findViewById(R.id.dinoAvatar);
        if (dinoAvatar != null && isAdded()) {
            Glide.with(this).load(R.drawable.dino_happy).into(dinoAvatar);
        }

        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);

        View backButton = view.findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> ScreenNav.back(this));
        }
        View homeBrandButton = view.findViewById(R.id.homeBrandButton);
        if (homeBrandButton != null) {
            homeBrandButton.setOnClickListener(v -> ScreenNav.showDashboard(this));
        }

        setUpVoice();
    }

    // ==================================================================
    //  Voice, delegated to the team's VoiceManager
    // ==================================================================

    private void setUpVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            if (commandBubble != null) {
                commandBubble.setText(R.string.voice_unavailable);
            }
            if (micButton != null) {
                micButton.setOnClickListener(v -> Toast
                        .makeText(requireContext(), R.string.voice_unavailable, Toast.LENGTH_SHORT)
                        .show());
            }
            return;
        }

        voiceManager = new VoiceManager(requireContext(), new VoiceListener());

        if (micButton != null) {
            micButton.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startListening();
                } else {
                    requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                }
            });
        }
    }

    private void startListening() {
        if (voiceManager == null) {
            return;
        }
        try {
            voiceManager.startListening();
        } catch (RuntimeException e) {
            // Some devices throw when the recogniser service is busy.
            if (commandBubble != null) {
                commandBubble.setText(R.string.voice_retry);
            }
        }
    }

    /** Mirrors the prototype's onstart / onend / onerror / onresult handlers. */
    private final class VoiceListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            if (readyText != null) {
                readyText.setText(R.string.voice_status_listening);
            }
            if (voiceStatus != null) {
                voiceStatus.setText(R.string.voice_status_ready);
            }
            startPulse();
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            stopPulse();
            if (readyText != null) {
                readyText.setText(R.string.voice_cta);
            }
        }

        @Override
        public void onError(int error) {
            stopPulse();
            if (readyText != null) {
                readyText.setText(R.string.voice_cta);
            }
            if (commandBubble != null) {
                commandBubble.setText(R.string.voice_retry);
            }
        }

        @Override
        public void onResults(Bundle results) {
            // Recognisers do not always deliver onEndOfSpeech, so stop here too.
            stopPulse();

            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches == null || matches.isEmpty()) {
                if (commandBubble != null) {
                    commandBubble.setText(R.string.voice_retry);
                }
                return;
            }

            String transcript = matches.get(0).trim();
            if (commandBubble != null) {
                commandBubble.setText("\"" + transcript + "\"");
            }
            handleTranscript(transcript);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }

    // ==================================================================
    //  Command table - the same one the prototype matched
    // ==================================================================

    /**
     * The prototype's regex table, kept in the same order so the first match wins. Each
     * branch is a contains check on the lowercased transcript, which is what the
     * prototype's word-boundary regexes amount to for real utterances.
     */
    private void handleTranscript(@NonNull String transcript) {
        String q = transcript.toLowerCase(Locale.US);

        if (q.contains("home") || q.contains("dashboard") || q.contains("main page")) {
            ScreenNav.showDashboard(this);
        } else if (q.contains("add task") || q.contains("new task") || q.contains("create task")) {
            new AddTaskSheetFragment().show(getChildFragmentManager(), "add_task");
        } else if (q.contains("schedule") || q.contains("reschedule")
                || q.contains("shift") || q.contains("calendar")) {
            // The schedule lives on the dashboard.
            ScreenNav.showDashboard(this);
        } else if (q.contains("biometric") || q.contains("health")
                || q.contains("sleep") || q.contains("hrv")) {
            ScreenNav.showBiometrics(this);
        } else if (q.contains("setting") || q.contains("privacy")) {
            ScreenNav.showSettings(this);
        } else if (q.contains("daily pop-up") || q.contains("daily pop up")
                || q.contains("daily harvest")) {
            ScreenNav.showDailyHarvest(this);
        } else if (q.contains("weekly pop-up") || q.contains("weekly pop up")
                || q.contains("feast")) {
            ScreenNav.showFeast(this);
        } else if (q.contains("back") || q.contains("close")) {
            ScreenNav.back(this);
        }
        // Anything else is echoed back into the bubble for the user to see.
    }

    // ==================================================================
    //  Listening pulse
    // ==================================================================

    /** The prototype's pulse class on the mic, as a scale loop. */
    private void startPulse() {
        if (micButton == null || pulseAnimator != null) {
            return;
        }
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(micButton,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f));
        pulseAnimator.setDuration(450);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.start();
    }

    private void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (micButton != null) {
            micButton.setScaleX(1f);
            micButton.setScaleY(1f);
        }
    }

    // ==================================================================
    //  Lifecycle
    // ==================================================================

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        stopPulse();

        // VoiceManager holds a binder to a system service; leaving it alive past the view
        // leaks the fragment. Its own destroy() is used rather than reaching inside.
        if (voiceManager != null) {
            voiceManager.destroy();
            voiceManager = null;
        }

        commandBubble = null;
        voiceStatus = null;
        readyText = null;
        micButton = null;
    }
}