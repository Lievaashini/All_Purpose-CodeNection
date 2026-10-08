package com.example.codenection2026_package.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.engine.RebalancePlanner;
import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.Task;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * The triage sheet opened by the dashboard's Rebalance button.
 *
 * <p>Port of the #triageModal block in Prototype/dashboard.html: two impact metrics,
 * then the plan itself, then the accept and dismiss actions.
 *
 * <p>The plan rows used to be four fixed rows in the layout, naming tasks ("Read Chapter 4
 * (Graphs)") that the user did not have. They are now inflated one per real task from the
 * {@link RebalancePlanner.Plan} the dashboard hands over, so the sheet shows what the model
 * actually decided about the day being looked at.
 *
 * <p>Accepting dismisses the sheet and fires {@link OnRebalanceAccepted}. Nothing is
 * rescheduled yet: the sheet is still a preview, because the day a task would move to is
 * the next piece of work. {@link RebalancePlanner.Proposal#getTargetDate()} is where it
 * will arrive.
 *
 * <p>Every line of prose is resolved through {@link CoachVoice} where the app is talking,
 * and through plain resources where it is reporting measurements.
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
     * which simply means no toast.
     */
    @Nullable
    private OnRebalanceAccepted listener;

    /**
     * The plan to render. Held as a field for the same reason as the listener - it is a
     * measurement of a moment, and a restored sheet should re-measure rather than show a
     * stale one. A null plan renders the empty state.
     */
    @Nullable
    private RebalancePlanner.Plan plan;

    public TriageSheetFragment() {
        super(R.layout.sheet_triage);
    }

    /** Sets the callback fired after the user accepts the proposal. */
    public void setOnRebalanceAccepted(@Nullable OnRebalanceAccepted listener) {
        this.listener = listener;
    }

    /** Hands the sheet the plan it should draw. Called before {@code show()}. */
    public void setPlan(@Nullable RebalancePlanner.Plan plan) {
        this.plan = plan;
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
        bindMetrics(view);
        bindPlan(view);

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

    // ==================================================================
    //  The plan
    // ==================================================================

    /**
     * Fills the plan list with one row per proposal, or shows the empty state.
     *
     * <p>Three different nothing-to-do cases get three different sentences, because
     * "the day is calm" and "everything on it is immovable" are not the same news.
     */
    private void bindPlan(@NonNull View view) {
        LinearLayout list = view.findViewById(R.id.triagePlanList);
        TextView empty = view.findViewById(R.id.triageEmptyState);
        if (list == null) {
            return;
        }
        list.removeAllViews();

        if (plan == null || plan.getProposals().isEmpty()) {
            showEmptyState(list, empty, R.string.triage_empty_no_tasks);
            return;
        }

        if (!plan.isOverThreshold()) {
            showEmptyState(list, empty, R.string.triage_empty_under_threshold);
            return;
        }

        if (plan.movedCount() == 0) {
            showEmptyState(list, empty, R.string.triage_empty_nothing_movable);
            return;
        }

        if (empty != null) {
            empty.setVisibility(View.GONE);
        }
        list.setVisibility(View.VISIBLE);

        // Accept now changes real data, so it is only offered when there is something for it
        // to change. Rows with "No lighter day in range" alone leave nothing to accept.
        setAcceptVisible(view, plan.placedCount() > 0);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (RebalancePlanner.Proposal proposal : plan.getProposals()) {
            list.addView(buildRow(inflater, list, proposal));
        }
    }

    private void showEmptyState(@NonNull LinearLayout list,
                                @Nullable TextView empty,
                                @StringRes int message) {
        list.setVisibility(View.GONE);
        if (empty != null) {
            empty.setText(message);
            empty.setVisibility(View.VISIBLE);
        }
        View root = getView();
        if (root != null) {
            setAcceptVisible(root, false);
        }
    }

    private static void setAcceptVisible(@NonNull View root, boolean visible) {
        View accept = root.findViewById(R.id.triageAccept);
        if (accept != null) {
            accept.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** One proposal row: accent bar, task name, time, and the outcome tag. */
    @NonNull
    private View buildRow(@NonNull LayoutInflater inflater,
                          @NonNull ViewGroup parent,
                          @NonNull RebalancePlanner.Proposal proposal) {

        View row = inflater.inflate(R.layout.item_triage_row, parent, false);
        Task task = proposal.getTask();

        TextView title = row.findViewById(R.id.triageRowTitle);
        if (title != null) {
            title.setText(task.getTaskName());
        }

        TextView sub = row.findViewById(R.id.triageRowSub);
        if (sub != null) {
            sub.setText(subtitleFor(proposal));
        }

        View accent = row.findViewById(R.id.triageRowAccent);
        if (accent != null) {
            accent.setBackgroundResource(accentFor(proposal.getOutcome()));
        }

        TextView tag = row.findViewById(R.id.triageRowTag);
        if (tag != null) {
            tag.setText(tagLabelFor(proposal.getOutcome()));
            tag.setBackgroundResource(tagBackgroundFor(proposal.getOutcome()));
            tag.setTextColor(ContextCompat.getColor(requireContext(),
                    tagColorFor(proposal.getOutcome())));
        }

        return row;
    }

    /**
     * What the row says under the task name.
     *
     * <p>A moved task has no destination yet, so it says so rather than naming a day it
     * cannot know. Claiming "Moved to Thu 10:00 AM" before the day is actually chosen is
     * exactly the fiction this screen is being rebuilt to remove.
     */
    @NonNull
    private String subtitleFor(@NonNull RebalancePlanner.Proposal proposal) {
        Task task = proposal.getTask();
        String window = getString(R.string.triage_row_time,
                nullSafe(task.getStartTime()), nullSafe(task.getEndTime()));

        if (proposal.getOutcome() != RebalancePlanner.Outcome.MOVED) {
            return window;
        }
        String target = proposal.getTargetDate();
        return target == null
                ? getString(R.string.triage_row_no_slot, window)
                : getString(R.string.triage_row_move_to, window, dayNameOf(target));
    }

    /**
     * Turns "2026-10-09" into "Thu" for the row.
     *
     * <p>A weekday name is what makes the proposal legible at a glance; an ISO date is
     * what the planner needs to work in. The conversion belongs here, at the edge, so the
     * engine never has to think about how a date reads.
     */
    @NonNull
    private String dayNameOf(@NonNull String isoDate) {
        try {
            return LocalDate.parse(isoDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    .getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault());
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }

    @NonNull
    private static String nullSafe(@Nullable String value) {
        return value == null ? "" : value;
    }

    @DrawableRes
    private static int accentFor(@NonNull RebalancePlanner.Outcome outcome) {
        switch (outcome) {
            case PROTECTED:
                return R.drawable.bg_accent_blue;
            case MOVED:
                return R.drawable.bg_accent_amber;
            case KEPT:
            default:
                return R.drawable.bg_accent_mint;
        }
    }

    @StringRes
    private static int tagLabelFor(@NonNull RebalancePlanner.Outcome outcome) {
        switch (outcome) {
            case PROTECTED:
                return R.string.triage_tag_protected;
            case MOVED:
                return R.string.triage_tag_postponed;
            case KEPT:
            default:
                return R.string.triage_tag_kept;
        }
    }

    @DrawableRes
    private static int tagBackgroundFor(@NonNull RebalancePlanner.Outcome outcome) {
        switch (outcome) {
            case PROTECTED:
                return R.drawable.bg_tag_blue;
            case MOVED:
                return R.drawable.bg_tag_amber;
            case KEPT:
            default:
                return R.drawable.bg_tag_mint;
        }
    }

    @ColorRes
    private static int tagColorFor(@NonNull RebalancePlanner.Outcome outcome) {
        switch (outcome) {
            case PROTECTED:
                return R.color.secondary_blue;
            case MOVED:
                return R.color.status_amber;
            case KEPT:
            default:
                return R.color.brand_mint;
        }
    }

    // ==================================================================
    //  Metrics
    // ==================================================================

    /**
     * The "Cognitive Load" headline, measured rather than asserted.
     *
     * <p>It reports the load this plan takes off the day, which is knowable without
     * knowing where anything lands. The sleep figure beside it is left to the layout:
     * projected sleep recovery is not something the current inputs can support, and a
     * made-up number is what this screen is losing.
     */
    private void bindMetrics(@NonNull View view) {
        TextView loadValue = view.findViewById(R.id.triageMetricLoadValue);
        if (loadValue == null) {
            return;
        }
        int reduction = plan == null ? 0 : plan.getLoadReductionPercent();
        loadValue.setText(getString(R.string.triage_metric_load_reduction, reduction));
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
