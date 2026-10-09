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
import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.addtask.AddTaskSheetFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.example.codenection2026_package.ui.shell.ScreenNav;
import com.example.codenection2026_package.ui.shell.ToneCopy;

import java.util.ArrayList;

/**
 * SCREEN 9 - COMPANION / VOICE. Port of Prototype/companion.html.
 *
 * <p><b>This screen reuses the team's VoiceManager.</b> The prototype drives the Web
 * Speech API from a page-local script; on Android that job already belongs to
 * api/VoiceManager.java, which wraps SpeechRecognizer and sets EXTRA_PREFER_OFFLINE for
 * the app's offline-first pitch. This fragment therefore does NOT create its own
 * recognizer - it hands VoiceManager a RecognitionListener and forwards the transcript
 * into {@link VoiceCommandRouter}, exactly as the prototype forwarded it to
 * window.location.
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
                            commandBubble.setText(CoachVoice.Line.VOICE_UNAVAILABLE.pick(tone()));
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

        // The layout cannot know the coaching tone, so the screen's spoken-at-the-user copy
        // is resolved here. The layout keeps the Hype variant as preview and fallback.
        ToneCopy.on(view, tone())
                .set(R.id.voiceInstruction, CoachVoice.Line.VOICE_INSTRUCTION)
                .set(R.id.readyText, CoachVoice.Line.VOICE_CTA);

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
            if (commandBubble != null) {
                commandBubble.setText(CoachVoice.Line.VOICE_UNAVAILABLE.pick(tone()));
            }
            if (micButton != null) {
                micButton.setOnClickListener(v -> Toast
                        .makeText(requireContext(),
                                CoachVoice.Line.VOICE_UNAVAILABLE.pick(tone()),
                                Toast.LENGTH_SHORT)
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
                commandBubble.setText(CoachVoice.Line.VOICE_RETRY.pick(tone()));
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
                readyText.setText(CoachVoice.Line.VOICE_CTA.pick(tone()));
            }
        }

        @Override
        public void onError(int error) {
            stopPulse();
            if (readyText != null) {
                readyText.setText(CoachVoice.Line.VOICE_CTA.pick(tone()));
            }
            if (commandBubble != null) {
                commandBubble.setText(CoachVoice.Line.VOICE_RETRY.pick(tone()));
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
                    commandBubble.setText(CoachVoice.Line.VOICE_RETRY.pick(tone()));
                }
                return;
            }

            String transcript = matches.get(0).trim();
            if (commandBubble != null) {
                commandBubble.setText(getString(R.string.voice_transcript_quoted, transcript));
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
    //  Command table - shared with the onboarding voice popup
    // ==================================================================

    /**
     * Asks {@link VoiceCommandRouter} which screen the user asked for, then goes there.
     *
     * <p>The words themselves live in the router, so this screen and the onboarding
     * {@link VoiceDinoDialogFragment} recognise exactly the same commands. Only the acting
     * half is local, because a dialog navigates differently from a full screen.
     */
    private void handleTranscript(@NonNull String transcript) {
        switch (VoiceCommandRouter.route(transcript)) {
            case ADD_TASK:
                new AddTaskSheetFragment().show(getChildFragmentManager(), "add_task");
                break;
            case BIOMETRICS:
                ScreenNav.showBiometrics(this);
                break;
            case SETTINGS:
                ScreenNav.showSettings(this);
                break;
            case HARD_LIMITS:
                ScreenNav.showHardLimits(this);
                break;
            case DAILY_HARVEST:
                ScreenNav.showDailyHarvest(this);
                break;
            case FEAST:
                ScreenNav.showFeast(this);
                break;
            case DASHBOARD:
                ScreenNav.showDashboard(this);
                break;
            case BACK:
                ScreenNav.back(this);
                break;
            default:
                // NONE: the transcript is already echoed in the bubble, so the user can see
                // what was heard and stay put rather than being sent somewhere random.
                break;
        }
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