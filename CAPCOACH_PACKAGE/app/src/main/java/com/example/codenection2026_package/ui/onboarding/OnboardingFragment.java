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
import com.example.codenection2026_package.model.ToneType;
import com.google.android.material.card.MaterialCardView;

/**
 * SCREEN 1 - Onboarding. Port of {@code prototype/onboarding.html}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Coaching tone selection (3 cards, exactly one active)</li>
 *   <li>Dino dialogue updates when the tone changes</li>
 *   <li>Health Connect "LINK / READY" toggle - UI only, see note below</li>
 *   <li>Theme toggle, and navigation to Screen 2</li>
 * </ul>
 *
 * <p><b>Handoff note:</b> the health card is a <i>visual</i> toggle here. Role 4 owns the
 * real Health Connect permission flow and should call
 * {@link #setHealthConnectState(boolean)} when the permission result arrives.
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

        dinoDialogue = view.findViewById(R.id.dinoDialogue);
        healthStatusDot = view.findViewById(R.id.healthStatusDot);
        healthStatusText = view.findViewById(R.id.healthStatusText);

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
        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);

        // --- Tone cards ---
        cardHype = view.findViewById(R.id.toneCardHype);
        cardChill = view.findViewById(R.id.toneCardChill);
        cardPlain = view.findViewById(R.id.toneCardPlain);

        bindToneCard(cardHype, ToneType.HYPE);
        bindToneCard(cardChill, ToneType.CHILL);
        bindToneCard(cardPlain, ToneType.PLAIN);

        selectTone(ToneType.HYPE, false);

        // --- Health Connect card (visual toggle until Role 4 wires the real flow) ---
        View healthCard = view.findViewById(R.id.healthConnectCard);
        if (healthCard != null) {
            healthCard.setOnClickListener(v -> setHealthConnectState(!healthConnectGranted));
        }

        // --- Mic button - Role 4 replaces this with the SpeechRecognizer entry point ---
        View micButton = view.findViewById(R.id.micButton);
        if (micButton != null) {
            micButton.setOnClickListener(v ->
                    toast(getString(R.string.cd_mic_button)));
        }

        View talkButton = view.findViewById(R.id.talkToMeButton);
        if (talkButton != null) {
            talkButton.setOnClickListener(v ->
                    toast(getString(R.string.cd_mic_button)));
        }

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

    // ==================================================================
    //  Tone selection
    // ==================================================================

    private void bindToneCard(@Nullable MaterialCardView card, @NonNull ToneType tone) {
        if (card == null) {
            return;
        }
        card.setChecked(tone == selectedTone);
        card.setOnClickListener(v -> selectTone(tone, true));
    }

    /**
     * Applies the selection state to all three cards.
     *
     * <p>The card fill and stroke swap automatically through
     * {@code @color/selector_tone_stroke} and {@code @color/selector_tone_fill}.
     * What needs Java is the radio indicator glyph and the icon tick colour, because
     * {@code <include>} prevents those from being addressed as separate view ids.
     */
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

    /** @return the tone the student has selected. Persist this on the User entity. */
    @NonNull
    public ToneType getSelectedTone() {
        return selectedTone;
    }

    // ==================================================================
    //  Health Connect state (UI only)
    // ==================================================================

    /**
     * Switches the health card pill between LINK (idle) and READY (granted).
     *
     * <p><b>Role 4:</b> call this from the Health Connect permission callback. Do not
     * duplicate the visual logic.
     */
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

    public boolean isHealthConnectGranted() {
        return healthConnectGranted;
    }

    private void toast(String message) {
        if (isAdded() && getContext() != null) {
            android.widget.Toast.makeText(getContext(), message,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
