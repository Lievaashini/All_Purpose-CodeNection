package com.example.codenection2026_package.ui.shell;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.ToneType;

/**
 * Writes a screen's coaching-tone prose into its views.
 *
 * <p>Tone-aware copy cannot live in a layout: a layout has no idea which coaching tone the
 * user picked, so every such string has to be resolved at runtime. Doing that one
 * {@code setText} at a time across ten screens is a lot of near-identical noise, so screens
 * declare what varies in a single chained call instead:
 *
 * <pre>
 *   ToneCopy.on(view, tone)
 *           .set(R.id.limitsSubtitle, CoachVoice.Line.LIMITS_SUBTITLE)
 *           .set(R.id.workStatusLabel, CoachVoice.Line.WORK_STATUS_SAFE);
 * </pre>
 *
 * <p>Each {@code set} is a no-op when the id is missing or is not a TextView, so a screen
 * missing one line of copy loses that line rather than crashing - the same tolerance the
 * rest of the shell's view binding uses.
 *
 * <p><b>Argument-taking slots are not handled here.</b> A slot like the "saved outside this
 * week" toast needs a formatted value, so those call sites resolve the line themselves with
 * {@code getString(line.pick(tone), arg...)}.
 */
public final class ToneCopy {

    private final View root;
    @Nullable
    private final ToneType tone;

    private ToneCopy(@NonNull View root, @Nullable ToneType tone) {
        this.root = root;
        this.tone = tone;
    }

    /** Starts a binding pass over {@code root}. */
    @NonNull
    public static ToneCopy on(@NonNull View root, @Nullable ToneType tone) {
        return new ToneCopy(root, tone);
    }

    /** Sets one view's text from the slot's wording for the bound tone. */
    @NonNull
    public ToneCopy set(@IdRes int viewId, @NonNull CoachVoice.Line line) {
        View target = root.findViewById(viewId);
        if (target instanceof TextView) {
            ((TextView) target).setText(line.pick(tone));
        }
        return this;
    }
}
