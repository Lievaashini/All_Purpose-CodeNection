package com.example.codenection2026_package.ui.settings;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.codenection2026_package.R;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.google.android.material.card.MaterialCardView;

/**
 * The three coach-tone cards on Settings.
 *
 * <p>Owns the whole concern - which tone is selected, painting the cards, persisting the
 * choice, and the preview line - so {@link SettingsFragment} only has to call {@link #bind()}.
 *
 * <p>Until this existed nothing read or wrote the tone at all: Screen 1 let the user pick one
 * and {@link OnboardingPrefs#saveTone} was never called, so the choice was lost the moment the
 * screen was left. This is the first real writer, which also means the value Screen 1 shows
 * and the value stored here can now disagree - see the note on {@link #bind()}.
 *
 * <p>The cards are the very same {@code item_tone_*.xml} layouts Screen 1 inflates. The
 * painting below deliberately mirrors {@code OnboardingFragment.applyToneCard}: the card's
 * checked state drives the stroke and fill through {@code selector_tone_stroke/fill}, the
 * indicator circle swaps between mint and hollow, and each tone's icon keeps its own accent
 * colour whether or not it is the selected one (Hype mint, Chill blue, Plain gold).
 */
final class ToneSelector {

    private final Fragment host;
    private final MaterialCardView hypeCard;
    private final MaterialCardView chillCard;
    private final MaterialCardView plainCard;
    private final TextView previewText;

    @NonNull
    private ToneType selected = ToneType.HYPE;

    ToneSelector(@NonNull Fragment host, @NonNull View root) {
        this.host = host;
        this.hypeCard = root.findViewById(R.id.settingsToneHype);
        this.chillCard = root.findViewById(R.id.settingsToneChill);
        this.plainCard = root.findViewById(R.id.settingsTonePlain);
        this.previewText = root.findViewById(R.id.tonePreviewText);
    }

    /**
     * Restores the saved tone and wires the cards.
     *
     * <p>Reads from prefs rather than assuming {@link ToneType#HYPE}: a user who chose Chill on
     * Screen 1 must find Chill selected here. Screen 1 itself does not reload the saved value -
     * it always starts on Hype - so the two screens agree only after a choice is made here or
     * there. Making Screen 1 restore the saved tone too would close that gap; it is left alone
     * here because it is outside this screen.
     */
    void bind() {
        if (host.getContext() == null) {
            return;
        }
        selected = OnboardingPrefs.getTone(host.requireContext());

        if (hypeCard != null) {
            hypeCard.setOnClickListener(v -> select(ToneType.HYPE));
        }
        if (chillCard != null) {
            chillCard.setOnClickListener(v -> select(ToneType.CHILL));
        }
        if (plainCard != null) {
            plainCard.setOnClickListener(v -> select(ToneType.PLAIN));
        }
        render();
    }

    /** Settings applies immediately, the way the theme switch above it does. */
    private void select(@NonNull ToneType tone) {
        selected = tone;
        if (host.getContext() != null) {
            OnboardingPrefs.saveTone(host.requireContext(), tone);
        }
        render();
    }

    private void render() {
        paint(hypeCard, selected == ToneType.HYPE, R.color.brand_mint);
        paint(chillCard, selected == ToneType.CHILL, R.color.secondary_blue);
        paint(plainCard, selected == ToneType.PLAIN, R.color.tertiary_gold_container);

        if (previewText != null) {
            previewText.setText(host.getString(
                    R.string.settings_tone_preview, host.getString(selected.speechRes)));
        }
    }

    private void paint(@Nullable MaterialCardView card,
                       boolean checked,
                       @ColorRes int accentRes) {
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
        if (toneIcon != null && host.getContext() != null) {
            ColorStateList tint =
                    ContextCompat.getColorStateList(host.requireContext(), accentRes);
            if (tint != null) {
                toneIcon.setImageTintList(tint);
            }
        }
    }
}
