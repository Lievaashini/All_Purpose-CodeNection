package com.example.codenection2026_package.ui.companion;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.api.VoiceManager;
import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.addtask.AddTaskSheetFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.shell.ScreenNav;
import com.example.codenection2026_package.ui.shell.ToneCopy;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Locale;

/**
 * The simplified, Dino-focused voice popup opened from the onboarding microphone.
 *
 * <p>The onboarding screen's TALK TO ME button and round mic button previously led to the
 * full {@link CompanionFragment}, which is a big screen dominated by chrome. This dialog
 * is the smaller cousin the user actually wants at that moment: the Dino, one line of
 * dialogue, and one button to talk. It dismisses back to onboarding afterwards.
 *
 * <p><b>It reuses the team's {@link VoiceManager} rather than building a recogniser.</b>
 * The same class the companion screen uses is driven here with a local
 * {@link RecognitionListener}, so there is exactly one speech implementation in the app.
 *
 * <p>Recognised commands are routed through {@link ScreenNav}, matching the prototype's
 * own voice-command table; the dialog dismisses first so the target screen is not replaced
 * underneath a live dialog.
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public class VoiceDinoDialogFragment extends BottomSheetDialogFragment {

    private TextView voiceBubble;
    private TextView voiceStatusText;
    private TextView voiceReadyText;
    private View voiceMicButton;

    /** The team's speech wrapper. Created here, destroyed in onDestroyView. */
    private VoiceManager voiceManager;

    // Define a callback interface
    public interface OnVoiceResultListener {
        void onVoiceSpoken(String rawTranscript, String cleanTranscript);
    }

    private OnVoiceResultListener listener;

    public void setOnVoiceResultListener(OnVoiceResultListener listener) {
        this.listener = listener;
    }

    /** The prototype's browser microphone prompt, as an Android runtime request. */
    private final ActivityResultLauncher<String> requestMicPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (Boolean.TRUE.equals(isGranted)) {
                            startListening();
                        } else if (voiceBubble != null) {
                            voiceBubble.setText(CoachVoice.Line.VOICE_UNAVAILABLE.pick(tone()));
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_voice_dino, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        voiceBubble = view.findViewById(R.id.voiceBubble);
        voiceStatusText = view.findViewById(R.id.voiceStatusText);
        voiceReadyText = view.findViewById(R.id.voiceReadyText);
        voiceMicButton = view.findViewById(R.id.voiceMicButton);

        // Animated mascot: the asset is a GIF, so it must go through Glide.
        ImageView dino = view.findViewById(R.id.voiceDinoAvatar);
        if (dino != null && isAdded()) {
            Glide.with(this).load(R.drawable.dino_happy).into(dino);
        }

        View closeButton = view.findViewById(R.id.voiceCloseButton);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismiss());
        }

        // The layout cannot know the coaching tone, so the Dino's prompt and CTA are
        // resolved here. The layout keeps the Hype variant as preview and fallback.
        ToneCopy.on(view, tone())
                .set(R.id.voiceHint, CoachVoice.Line.VOICE_INSTRUCTION)
                .set(R.id.voiceReadyText, CoachVoice.Line.VOICE_CTA);

        setUpVoice();
    }

    /**
     * The coaching tone chosen in onboarding or Settings.
     *
     * <p>Null once the fragment is detached, which is reachable here because the permission
     * result and the recogniser callbacks can both outlive the view. A null tone is Hype in
     * {@link CoachVoice.Line#pick}, so a late callback falls back to the default voice
     * rather than throwing.
     */
    @Nullable
    private ToneType tone() {
        return isAdded() ? OnboardingPrefs.getTone(requireContext()) : null;
    }

    // ==================================================================
    //  Voice, delegated to the team's VoiceManager
    // ==================================================================

    private void setUpVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            if (voiceBubble != null) {
                voiceBubble.setText(CoachVoice.Line.VOICE_UNAVAILABLE.pick(tone()));
            }
            if (voiceMicButton != null) {
                voiceMicButton.setOnClickListener(v -> Toast
                        .makeText(requireContext(),
                                CoachVoice.Line.VOICE_UNAVAILABLE.pick(tone()),
                                Toast.LENGTH_SHORT)
                        .show());
            }
            return;
        }

        voiceManager = new VoiceManager(requireContext(), new VoiceListener());

        if (voiceMicButton != null) {
            voiceMicButton.setOnClickListener(v -> {
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
            // Some devices throw when the recogniser service is busy or was just cancelled.
            if (voiceBubble != null) {
                voiceBubble.setText(CoachVoice.Line.VOICE_RETRY.pick(tone()));
            }
        }
    }

    /** Mirrors the prototype's onstart / onend / onerror / onresult handlers. */
    private final class VoiceListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            if (voiceStatusText != null) {
                voiceStatusText.setText(R.string.voice_status_listening);
            }
            if (voiceReadyText != null) {
                voiceReadyText.setText(CoachVoice.Line.VOICE_CTA.pick(tone()));
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
        }

        @Override
        public void onError(int error) {
            stopPulse();
            if (voiceStatusText != null) {
                voiceStatusText.setText(R.string.voice_status_ready);
            }
            if (voiceBubble != null) {
                voiceBubble.setText(CoachVoice.Line.VOICE_RETRY.pick(tone()));
            }
        }

        @Override
        public void onResults(Bundle results) {
            stopPulse();
            if (voiceStatusText != null) {
                voiceStatusText.setText(R.string.voice_status_ready);
            }

            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches == null || matches.isEmpty()) {
                if (voiceBubble != null) {
                    voiceBubble.setText(CoachVoice.Line.VOICE_RETRY.pick(tone()));
                }
                return;
            }

            String transcript = matches.get(0).trim();
            if (voiceBubble != null) {
                voiceBubble.setText("\"" + transcript + "\"");
            }

            // Format with your VoiceInputFormatter
            String cleanTitle = com.example.codenection2026_package.api.VoiceInputFormatter.formatTaskTitle(transcript);

            // Notify the parent OnboardingFragment to update its dialogue bubble too!
            if (listener != null) {
                listener.onVoiceSpoken(transcript, cleanTitle);
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

    /** Nods the Dino while it listens, so the popup feels alive. */
    private void startPulse() {
        ImageView dino = getView() == null ? null : getView().findViewById(R.id.voiceDinoAvatar);
        if (dino == null) {
            return;
        }
        dino.animate().cancel();
        dino.setScaleX(0.96f);
        dino.setScaleY(0.96f);
        dino.animate().scaleX(1.06f).scaleY(1.06f)
                .setInterpolator(new OvershootInterpolator())
                .setDuration(320)
                .start();
    }

    private void stopPulse() {
        ImageView dino = getView() == null ? null : getView().findViewById(R.id.voiceDinoAvatar);
        if (dino == null) {
            return;
        }
        dino.animate().cancel();
        dino.setScaleX(1f);
        dino.setScaleY(1f);
    }

    // ==================================================================
    //  Command table - the same one the prototype matched
    // ==================================================================

    /**
     * Routes the transcript through {@link ScreenNav}, matching the ordering of the
     * prototype's own voice table so the first match wins.
     *
     * <p>The dialog dismisses before navigating, otherwise the target screen would be
     * swapped in underneath a dialog that is still showing.
     */
    private void handleTranscript(@NonNull String transcript) {
        String q = transcript.toLowerCase(Locale.US);

        if (q.contains("add task") || q.contains("new task") || q.contains("create task")) {
            dismiss();
            new AddTaskSheetFragment().show(getParentFragmentManager(), "add_task");
        } else if (q.contains("biometric") || q.contains("health")
                || q.contains("sleep") || q.contains("hrv")) {
            dismiss();
            ScreenNav.showBiometrics(this);
        } else if (q.contains("setting") || q.contains("privacy")) {
            dismiss();
            ScreenNav.showSettings(this);
        } else if (q.contains("daily pop-up") || q.contains("daily pop up")
                || q.contains("daily harvest")) {
            dismiss();
            ScreenNav.showDailyHarvest(this);
        } else if (q.contains("weekly pop-up") || q.contains("weekly pop up")
                || q.contains("feast")) {
            dismiss();
            ScreenNav.showFeast(this);
        } else if (q.contains("dashboard") || q.contains("home")
                || q.contains("schedule") || q.contains("shift") || q.contains("calendar")) {
            dismiss();
            ScreenNav.showDashboard(this);
        }
        // Anything else stays on screen with the transcript echoed in the bubble, so the
        // user can see what was heard and try again.
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

        voiceBubble = null;
        voiceStatusText = null;
        voiceReadyText = null;
        voiceMicButton = null;
    }
}

