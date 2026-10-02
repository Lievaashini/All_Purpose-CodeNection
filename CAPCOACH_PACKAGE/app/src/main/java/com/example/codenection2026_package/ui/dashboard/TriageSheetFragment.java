package com.example.codenection2026_package.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * The triage sheet opened by the dashboard's Rebalance button.
 *
 * <p>Port of the #triageModal block in Prototype/dashboard.html: two impact metrics,
 * then the four ways the optimiser can treat an item (protected, kept, postponed,
 * deferred), then the accept and dismiss actions.
 *
 * <p>Accepting dismisses the sheet and fires {@link OnRebalanceAccepted}, which the
 * dashboard uses to show its "Schedule Rebalanced" toast. That is the prototype's
 * acceptRebalance() -> closeTriageModal() + showToast() chain.
 *
 * <p>The sheet itself has no data dependency: every row is a fixed proposal, so it
 * renders on its own without the Room layer.
 *
 * <p>Every line of prose here is resolved through {@link CoachVoice}, because the sheet
 * cannot rely on the layout for it: the wording depends on the coaching tone, which a
 * layout cannot know. The metric figures (-55%, +3.5h) and the sample task names stay
 * fixed, since those are measurements and data rather than the app talking.
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public class TriageSheetFragment extends BottomSheetDialogFragment {

    /** Implemented by the dashboard so the sheet can report a confirmed rebalance. */
    public interface OnRebalanceAccepted {
        void onRebalanceAccepted();
    }

    /** Tag used by DashboardFragment when it shows this sheet. */
    public static final String TAG = "triage";

    /**
     * Held as a plain field rather than an argument: this dialog is transient, and if
     * the process is killed while it is up, Android restores it without a callback,
     * which simply means no toast. Documented rather than worked around.
     */
    @Nullable
    private OnRebalanceAccepted listener;

    public TriageSheetFragment() {
        super(R.layout.sheet_triage);
    }

    /** Sets the callback fired after the user accepts the proposal. */
    public void setOnRebalanceAccepted(@Nullable OnRebalanceAccepted listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_triage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindToneCopy(view);

        ImageView dino = view.findViewById(R.id.triageDino);
        if (dino != null) {
            // Animated GIF asset, so it must go through Glide.
            Glide.with(this).load(R.drawable.dino_happy).into(dino);
        }

        View close = view.findViewById(R.id.triageClose);
        if (close != null) {
            close.setOnClickListener(v -> dismiss());
        }

        View dismissButton = view.findViewById(R.id.triageDismiss);
        if (dismissButton != null) {
            dismissButton.setOnClickListener(v -> dismiss());
        }

        View accept = view.findViewById(R.id.triageAccept);
        if (accept != null) {
            accept.setOnClickListener(v -> {
                dismiss();
                if (listener != null) {
                    listener.onRebalanceAccepted();
                }
            });
        }
    }

    /**
     * Rewrites the sheet's prose in the user's coaching tone.
     *
     * <p>Null-tolerant per view: the sheet is a fixed layout, but a single missing id should
     * cost one line of copy rather than crash the whole proposal.
     */
    private void bindToneCopy(@NonNull View view) {
        if (getContext() == null) {
            return;
        }
        ToneType tone = OnboardingPrefs.getTone(requireContext());

        set(view, R.id.triageTitle, CoachVoice.Line.TRIAGE_TITLE, tone);
        set(view, R.id.triageMetricLoadSub, CoachVoice.Line.TRIAGE_LOAD_SUB, tone);
        set(view, R.id.triageMetricSleepSub, CoachVoice.Line.TRIAGE_SLEEP_SUB, tone);
        set(view, R.id.triageItemKeptSub, CoachVoice.Line.TRIAGE_KEPT_SUB, tone);
        set(view, R.id.triageItemPostponedSub, CoachVoice.Line.TRIAGE_POSTPONED_SUB, tone);
        set(view, R.id.triageItemDeferredSub, CoachVoice.Line.TRIAGE_DEFERRED_SUB, tone);
        set(view, R.id.triageAccept, CoachVoice.Line.TRIAGE_ACCEPT, tone);
        set(view, R.id.triageDismiss, CoachVoice.Line.TRIAGE_DISMISS, tone);
    }

    private void set(@NonNull View root,
                     int viewId,
                     @NonNull CoachVoice.Line line,
                     @NonNull ToneType tone) {
        View target = root.findViewById(viewId);
        if (target instanceof TextView) {
            ((TextView) target).setText(line.pick(tone));
        }
    }
}
