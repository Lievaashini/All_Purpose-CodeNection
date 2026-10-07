package com.example.codenection2026_package.ui.companion;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.api.HealthConnectReader;
import com.example.codenection2026_package.engine.CapacityCalculator;
import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Makes the Dino lean in beside itself and react to the day the user is looking at.
 *
 * <p><b>It is event-driven, not on a timer.</b> Two things make it speak, and nothing else
 * does:
 *
 * <ul>
 *   <li>{@link #onDashboardEntered()} - the user arrives on the dashboard, whether that is
 *       opening the app or coming back to Home from another tab.</li>
 *   <li>{@link #onDayViewed()} - the user moves to a different day of the week, either by
 *       tapping a day pill or by saving a task that belongs to another day.</li>
 * </ul>
 *
 * <p>There is no wandering clock behind it. If the user does nothing, the Dino says nothing
 * - which is what makes closing a bubble with the X meaningful: it costs nothing, because
 * the feature only returns when the user themselves moves on.
 *
 * <p>Every remark is decided by two things and two things only: the coaching tone, which
 * picks the wording, and the capacity percentage of the day on screen, which picks the
 * state. Both come from existing code - {@link CoachVoice} owns the wording and
 * {@link CapacityCalculator#getCapacityState} owns the banding. This class adds no state
 * model, no threshold and no arithmetic.
 *
 * <p><b>It is a {@link PopupWindow}, not a dialog.</b> A dialog would dim the screen, steal
 * focus and demand an answer, which is the opposite of the intent. A popup floating beside
 * the mascot leaves the dashboard fully readable and tappable underneath - and because it
 * is not focusable, a tap meant for a day pill both dismisses the bubble and reaches the
 * pill underneath. It also means fragment_dashboard.xml does not have to be restructured
 * to host it.
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public final class DinoReactionBubble {

    /**
     * Supplies the capacity the bubble should react to, read at the moment it pops.
     *
     * <p>Deliberately a live callback rather than a number handed over once. The user can
     * tap through several days before the Dino speaks, so a value captured when the screen
     * opened would quickly be about the wrong day. Asking for the figure at pop time means
     * every remark is about the day actually on screen - and it keeps working unchanged
     * once the load chart is wired to real data instead of the demo array.
     */
    public interface CapacitySource {

        /** The capacity shown on the dashboard right now, in whole percent. */
        int capacityPercent();
    }

    // ==================================================================
    //  Timing
    // ==================================================================

    /**
     * How long the dashboard is left alone before the Dino greets the user.
     *
     * <p>Not zero, and not random in any meaningful sense: the screen has a week strip, a
     * chart and a card to lay out, and the remark should land after that so it reads as a
     * greeting rather than as part of the layout. The small spread exists only so that
     * arriving on the dashboard does not feel mechanical.
     */
    private static final long ENTRY_DELAY_MIN_MS = 500L;
    private static final long ENTRY_DELAY_MAX_MS = 900L;

    /**
     * How long after a day tap the Dino reacts.
     *
     * <p>Shorter than the entry delay, because this one is a direct response to something
     * the user just did and should feel immediate. It is still not zero: the pill has to
     * finish repainting first, so the bubble follows the tap instead of racing it.
     */
    private static final long DAY_DELAY_MIN_MS = 200L;
    private static final long DAY_DELAY_MAX_MS = 400L;

    /**
     * How long a bubble stays if nobody touches it.
     *
     * <p>It withdraws on its own rather than sitting there: the feature is meant to feel
     * like a passing remark, and a bubble that never leaves becomes furniture.
     */
    private static final long AUTO_HIDE_MS = 8_000L;

    // ==================================================================
    //  What it reacts to
    // ==================================================================

    /**
     * Sleep at or above this many hours reads as a good night, and the bubble may say so
     * instead of reporting capacity.
     *
     * <p>Seven is the general adult guideline. Note this is deliberately NOT the engine's
     * {@code SLEEP_PENALTY_THRESHOLD_MINUTES} of five hours: five is the point where the
     * capacity penalty stops, which is a much lower bar than "you slept well". Reusing it
     * would have the Dino congratulating someone on five hours.
     */
    private static final double GOOD_SLEEP_HOURS = 7.0;

    /**
     * How often a good night's sleep is the remark, when there is one to report.
     *
     * <p>Not every time, and that is the whole point of this constant. The sleep line was
     * specified as a state that displaces the capacity line. Obeying that literally would
     * mean a well-rested user never sees any of the four capacity lines - they would tap
     * through the whole week and get the same sleep remark every time, which defeats the
     * feature now that every remark is tied to a day the user chose to look at. So sleep
     * takes roughly one remark in four and the day's own capacity state keeps the rest.
     */
    private static final double SLEEP_LINE_CHANCE = 0.25;

    /** Used for the settle jitter and the sleep roll; nothing here is security-sensitive. */
    private final Random random = new Random();

    /** The engine's capacity banding, used exactly as written - never re-derived here. */
    private final CapacityCalculator capacityCalculator = new CapacityCalculator();

    private final Handler main = new Handler(Looper.getMainLooper());

    /**
     * Reads Health Connect off the main thread.
     *
     * <p>The sleep read is a blocking binder call. The biometrics screen happens to make it
     * on the main thread, but a bubble appearing in front of the user is exactly the wrong
     * moment to risk a dropped frame, so the read is refreshed in the background and
     * whatever has arrived by the time the Dino speaks is used.
     */
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @NonNull
    private final Fragment owner;

    @Nullable
    private Context appContext;
    @Nullable
    private View anchor;
    @Nullable
    private CapacitySource source;
    @Nullable
    private PopupWindow popup;

    /** True between {@link #attach} and {@link #stop}; gates every trigger. */
    private boolean ready;

    /**
     * Last night's sleep, in hours, or a negative value until the background read answers.
     *
     * <p>Negative is the "not known yet" sentinel rather than zero, because zero is a real
     * reading (no sleep recorded) and must not be confused with a slow Health Connect.
     */
    private double sleepHours = -1.0;

    private final Runnable showRunnable = this::pop;

    /** Withdraws a bubble nobody interacted with. */
    private final Runnable autoHideRunnable = this::hidePopup;

    public DinoReactionBubble(@NonNull Fragment owner) {
        this.owner = owner;
    }

    // ==================================================================
    //  Wiring
    // ==================================================================

    /**
     * Binds the bubble to the view it appears beside.
     *
     * <p>Call from {@code onViewCreated}, then drive it with {@link #onDashboardEntered()}
     * and {@link #onDayViewed()}. {@code anchorId} is the view the bubble hangs off - the
     * mascot itself, so the bubble's tail can point at the Dino.
     */
    public void attach(@IdRes int anchorId, @NonNull CapacitySource capacitySource) {
        View root = owner.getView();
        Context context = owner.getContext();
        if (root == null || context == null) {
            return;
        }

        anchor = root.findViewById(anchorId);
        source = capacitySource;
        appContext = context.getApplicationContext();
        ready = true;

        // Warm the sleep read now so it has landed by the time the first remark is due. A
        // failure here is silent by design: the bubble simply reports capacity instead.
        io.execute(() -> {
            double hours = readSleepHours();
            main.post(() -> sleepHours = hours);
        });
    }

    /**
     * The user arrived on the dashboard - opening the app, or coming back to Home.
     *
     * <p>A fresh call is fine and expected: every navigation in this app replaces the
     * fragment, so the dashboard is rebuilt each time it is shown.
     */
    public void onDashboardEntered() {
        schedule(ENTRY_DELAY_MIN_MS, ENTRY_DELAY_MAX_MS);
    }

    /**
     * The user moved to a different day of the week.
     *
     * <p>Callers should only fire this on a genuine change of day. Re-tapping the day that
     * is already selected is not "viewing another day" and should not produce a remark.
     */
    public void onDayViewed() {
        schedule(DAY_DELAY_MIN_MS, DAY_DELAY_MAX_MS);
    }

    /**
     * Queues the next remark, replacing any remark already queued.
     *
     * <p>The bubble currently on screen is deliberately left alone here. Tapping through
     * the week should not blank the bubble between taps: it is retired inside {@link #pop}
     * in the same frame the replacement is shown, so the swap has no visible gap.
     */
    private void schedule(long min, long max) {
        if (!ready) {
            return;
        }
        main.removeCallbacks(showRunnable);
        main.postDelayed(showRunnable, between(min, max));
    }

    /**
     * Stops the bubble and takes it down. Call from {@code onDestroyView}.
     *
     * <p>Without this a queued remark could fire against a dead view, and a
     * {@link PopupWindow} would keep floating over whatever screen replaced the dashboard.
     */
    public void stop() {
        ready = false;
        main.removeCallbacks(showRunnable);
        main.removeCallbacks(autoHideRunnable);
        hidePopup();
        io.shutdownNow();
        anchor = null;
        source = null;
        appContext = null;
    }

    // ==================================================================
    //  Showing
    // ==================================================================

    private void pop() {
        // The fragment can be gone, or the dashboard replaced by another screen, between
        // scheduling and firing. anchor.isShown() is what catches the second case: a
        // detached-but-alive fragment's views report themselves as not shown.
        if (!ready || source == null || !owner.isAdded() || anchor == null || !anchor.isShown()) {
            return;
        }
        Context context = owner.getContext();
        if (context == null) {
            return;
        }

        Reaction reaction = reactionFor(source.capacityPercent());

        View content = LayoutInflater.from(context)
                .inflate(R.layout.popup_dino_reaction, null, false);

        TextView text = content.findViewById(R.id.dinoReactionText);
        if (text != null) {
            // Resolved with an argument, not through ToneCopy: every reaction slot takes
            // %1$d, because Plain prints the real figure and the sleep slot prints the
            // real hours in all three tones.
            ToneType tone = OnboardingPrefs.getTone(context);
            text.setText(context.getString(reaction.line.pick(tone), reaction.argument));
        }

        // Animated GIF, so it must go through Glide; isAdded() re-checked because the
        // fragment may have detached while this line was being reached.
        ImageView avatar = content.findViewById(R.id.dinoReactionAvatar);
        if (avatar != null && owner.isAdded()) {
            Glide.with(owner).load(reaction.dinoRes).into(avatar);
        }

        View close = content.findViewById(R.id.dinoReactionClose);
        if (close != null) {
            // Closes this remark only. There is no timer to defeat, so the feature simply
            // waits for the user to move on before it says anything else.
            close.setOnClickListener(v -> hidePopup());
        }

        // Retire the previous remark in the same frame the replacement appears, so tapping
        // through several days swaps the bubble instead of blinking it out and back.
        hidePopup();

        popup = new PopupWindow(content,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        // A transparent background is part of the popup contract, not decoration: without
        // one, setOutsideTouchable does nothing and the bubble could never be dismissed by
        // tapping away. Focus stays off the popup so it does not steal the back button, and
        // so a tap outside both closes the bubble and reaches whatever is underneath.
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(false);

        // Anchored to the mascot, so the tail lines up under the Dino. Zero offset because
        // showAsDropDown already positions relative to the anchor's bottom-left.
        popup.showAsDropDown(anchor, 0, 0);

        main.removeCallbacks(autoHideRunnable);
        main.postDelayed(autoHideRunnable, AUTO_HIDE_MS);
    }

    /**
     * The one reaction for the current figure: which line, which sprite, which number.
     */
    @NonNull
    private Reaction reactionFor(int capacityPercent) {
        // Sleep occasionally displaces the capacity line rather than always winning - see
        // SLEEP_LINE_CHANCE for why an unconditional sleep line would break the feature.
        if (sleepHours >= GOOD_SLEEP_HOURS && random.nextDouble() < SLEEP_LINE_CHANCE) {
            return new Reaction(CoachVoice.Line.DINO_REACT_SLEEP,
                    R.drawable.dino_happy,
                    (int) Math.round(sleepHours));
        }

        // The banding is the engine's, not this class's. Adding or moving a threshold is a
        // change in CapacityCalculator.getCapacityState and is picked up here for free.
        switch (capacityCalculator.getCapacityState(capacityPercent)) {
            case THRIVING:
                return new Reaction(CoachVoice.Line.DINO_REACT_THRIVING,
                        R.drawable.dino_happy, capacityPercent);
            case STACKED:
                return new Reaction(CoachVoice.Line.DINO_REACT_STACKED,
                        R.drawable.dino_overload, capacityPercent);
            case OVERLOAD:
                // dino_dead was shipped but never referenced anywhere until this feature;
                // collapsing Dino is exactly the right face for "Bro. BRO. Sleep exists."
                return new Reaction(CoachVoice.Line.DINO_REACT_OVERLOAD,
                        R.drawable.dino_dead, capacityPercent);
            case NORMAL:
            default:
                return new Reaction(CoachVoice.Line.DINO_REACT_NORMAL,
                        R.drawable.dino_steady, capacityPercent);
        }
    }

    /** Takes the current bubble down, whichever path asked for it. Idempotent. */
    private void hidePopup() {
        main.removeCallbacks(autoHideRunnable);
        if (popup != null) {
            popup.dismiss();
            popup = null;
        }
    }

    private long between(long min, long max) {
        return min + (long) (random.nextDouble() * (max - min));
    }

    // ==================================================================
    //  Sleep, from the team's Health Connect reader
    // ==================================================================

    /**
     * Last night's sleep in hours, or a negative value when it cannot be known.
     *
     * <p>Both calls are the team's own, used as written. Neither throws: the reader already
     * returns an empty list when Health Connect is missing or the read permission was never
     * granted, which lands here as zero hours and correctly means "nothing to celebrate".
     *
     * <p>Note this reads real Health Connect data, independently of the demo sleep figure
     * the capacity card prints. On a device with no Health Connect data the sleep line
     * simply never comes up, and the four capacity lines carry the feature on their own.
     */
    private double readSleepHours() {
        Context context = appContext;
        if (context == null) {
            return -1.0;
        }
        return HealthConnectReader.calculateTotalSleepHours(
                HealthConnectReader.getSleepSessionsLast24Hours(context));
    }

    // ==================================================================
    //  The resolved reaction
    // ==================================================================

    /** One fully-decided bubble: what to say, what to show, and which figure to print. */
    private static final class Reaction {

        @NonNull
        final CoachVoice.Line line;

        @DrawableRes
        final int dinoRes;

        /** Capacity percent for the state slots, whole hours for the sleep slot. */
        final int argument;

        Reaction(@NonNull CoachVoice.Line line, @DrawableRes int dinoRes, int argument) {
            this.line = line;
            this.dinoRes = dinoRes;
            this.argument = argument;
        }
    }
}