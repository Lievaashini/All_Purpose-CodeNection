package com.example.codenection2026_package.ui.harvest;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.example.codenection2026_package.ui.shell.ScreenNav;

/**
 * SCREEN 6 - DAILY HARVEST. Port of Prototype/daily-harvest.html.
 *
 * <p>The morning pop-up: apples that grew overnight from yesterday's completed tasks, a
 * Dino holding a basket, and the weekly feast meter.
 *
 * <p>The prototype's triggerHarvest() runs a staggered CSS keyframe - each apple falls
 * toward the basket 120ms after the last, then the Dino does a joy bounce, then the
 * counters tick up. That sequence is reproduced with ObjectAnimators rather than dropped,
 * because it is the whole point of the screen.
 *
 * <p>Both sprites are animated GIFs loaded through Glide. setImageResource would decode
 * only the first frame, so the animation would silently never play.
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public class DailyHarvestFragment extends Fragment {

    /** The prototype starts at 12 stashed and harvests 4 more. */
    private static final int STARTING_STASH = 12;
    private static final int FEAST_GOAL = 20;
    private static final int STREAK_DAY = 4;

    /**
     * How many tasks were completed yesterday, and therefore how many apples grew on the
     * tree overnight.
     *
     * <p>This is the number the whole screen is about: one completed task becomes one
     * apple. It is a constant here only because nothing else supplies it yet - hand this
     * the real count from Role 1's completed-task query (or the feast stash) and the tree,
     * the basket, the badge and the progress bar all follow automatically, because every
     * one of them is derived from this value rather than hard-coded in the layout.
     */
    private static final int APPLES_GROWN = 4;

    /**
     * Where apples sit on the tree crown, as a fraction of the stage size.
     *
     * <p>Laid out around the canopy rather than at the four corners of the stage: the
     * earlier version pinned apples to the stage edges, which read as random red dots
     * floating beside the tree instead of fruit hanging on it. Extra apples beyond this
     * list reuse the positions from the start, so any count renders sensibly.
     */
    private static final float[][] APPLE_SPOTS = {
            {0.34f, 0.22f},
            {0.62f, 0.18f},
            {0.26f, 0.40f},
            {0.70f, 0.36f},
            {0.45f, 0.30f},
            {0.56f, 0.47f},
            {0.36f, 0.53f},
            {0.74f, 0.55f}
    };

    private TextView basketLabel;
    private TextView stashSubtitle;
    private TextView progressPercentage;
    private TextView progressRatio;
    private TextView dinoChatBubble;
    private TextView collectButton;
    private View feastProgressBar;
    private View harvestToast;
    private ViewGroup appleContainer;
    private ImageView dinoSprite;

    /** True once the apples have been collected; the CTA then becomes "start my day". */
    private boolean collected = false;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_daily_harvest, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);
        dinoSprite = view.findViewById(R.id.dinoSprite);
        basketLabel = view.findViewById(R.id.basketLabel);
        stashSubtitle = view.findViewById(R.id.stashSubtitle);
        progressPercentage = view.findViewById(R.id.progressPercentage);
        progressRatio = view.findViewById(R.id.progressRatio);
        dinoChatBubble = view.findViewById(R.id.dinoChatBubble);
        feastProgressBar = view.findViewById(R.id.feastProgressBar);
        harvestToast = view.findViewById(R.id.harvestToast);
        collectButton = view.findViewById(R.id.collectButton);

        View apples = view.findViewById(R.id.appleContainer);
        if (apples instanceof ViewGroup) {
            appleContainer = (ViewGroup) apples;
        }

        TextView streakText = view.findViewById(R.id.streakText);
        if (streakText != null) {
            streakText.setText(getString(R.string.harvest_streak_badge, STREAK_DAY));
        }
        TextView tooltipBadge = view.findViewById(R.id.tooltipBadge);
        if (tooltipBadge != null) {
            tooltipBadge.setText(getString(R.string.harvest_tooltip_badge, APPLES_GROWN));
        }
        TextView milestoneMid = view.findViewById(R.id.milestoneMid);
        if (milestoneMid != null) {
            milestoneMid.setText(getString(R.string.harvest_milestone_mid, FEAST_GOAL / 2));
        }
        TextView milestoneGoal = view.findViewById(R.id.milestoneGoal);
        if (milestoneGoal != null) {
            milestoneGoal.setText(getString(R.string.harvest_milestone_goal, FEAST_GOAL));
        }

        // The tree is a STATIC blank vector, not the apple_tree_full GIF. It must render
        // empty and still: the only fruit on it is the apples added by growApples() from
        // yesterday's completed tasks, so an animated pre-loaded crop would contradict
        // what the screen is telling the user. The Dino stays an animated GIF.
        if (isAdded()) {
            Glide.with(this).load(R.drawable.dino_happy).into(dinoSprite);
        }

        growApples();
        renderCounters(STARTING_STASH);
        wireActions(view);
    }

    // ==================================================================
    //  Apples grown on the tree
    // ==================================================================

    /**
     * Places one apple on the tree crown per task completed yesterday.
     *
     * <p>This is the screen's core idea: completed tasks turn into apples overnight. The
     * layout ships an empty apple container, so a day with no completed tasks shows a bare
     * tree, and the crop always matches the real count instead of a fixed four.
     *
     * <p>Positions come from {@link #APPLE_SPOTS}, and the apples fade and scale in so the
     * crop reads as something that grew rather than popped into existence.
     */
    private void growApples() {
        if (appleContainer == null) {
            return;
        }
        appleContainer.removeAllViews();

        int size = Math.round(getResources().getDimension(R.dimen.apple_size));

        appleContainer.post(() -> {
            int stageWidth = appleContainer.getWidth();
            int stageHeight = appleContainer.getHeight();
            if (stageWidth <= 0 || stageHeight <= 0) {
                return;
            }

            for (int i = 0; i < APPLES_GROWN; i++) {
                float[] spot = APPLE_SPOTS[i % APPLE_SPOTS.length];

                View apple = new View(requireContext());
                apple.setBackgroundResource(R.drawable.bg_apple);

                android.widget.FrameLayout.LayoutParams params =
                        new android.widget.FrameLayout.LayoutParams(size, size);
                params.leftMargin = Math.round(stageWidth * spot[0]) - (size / 2);
                params.topMargin = Math.round(stageHeight * spot[1]) - (size / 2);

                // Each apple is tappable - tapping one makes the Dino cheer.
                apple.setClickable(true);
                apple.setContentDescription(getString(R.string.cd_feed_apple));
                apple.setOnClickListener(v -> cheerDino());

                appleContainer.addView(apple, params);

                // Grow in, staggered so the crop appears one fruit at a time.
                apple.setScaleX(0f);
                apple.setScaleY(0f);
                apple.setAlpha(0f);
                apple.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setStartDelay(140L * i)
                        .setDuration(300)
                        .setInterpolator(new OvershootInterpolator())
                        .start();
            }
        });
    }

    // ==================================================================
    //  Counters
    // ==================================================================

    /**
     * Writes the basket total, the feast percentage and the progress bar width.
     *
     * <p>The bar is a plain View sized as a fraction of its track, because an XML
     * percentage width resolves against the parent rather than a value range.
     */
    private void renderCounters(int stash) {
        if (basketLabel != null) {
            basketLabel.setText(String.valueOf(stash));
        }
        if (stashSubtitle != null) {
            stashSubtitle.setText(getString(R.string.harvest_stash_label,
                    getString(R.string.harvest_stash_apples, stash)));
        }

        int percent = Math.min(100, Math.round(stash * 100f / FEAST_GOAL));
        if (progressPercentage != null) {
            progressPercentage.setText(percent + "%");
        }
        if (progressRatio != null) {
            progressRatio.setText(getString(R.string.harvest_progress_ratio, stash, FEAST_GOAL));
        }
        setBarFraction(feastProgressBar, percent / 100f);
    }

    private void setBarFraction(@Nullable View bar, float fraction) {
        if (bar == null || !(bar.getParent() instanceof View)) {
            return;
        }
        View track = (View) bar.getParent();
        bar.post(() -> {
            int width = track.getWidth();
            if (width <= 0) {
                return;
            }
            ViewGroup.LayoutParams params = bar.getLayoutParams();
            params.width = Math.round(width * Math.max(0f, Math.min(1f, fraction)));
            bar.setLayoutParams(params);
        });
    }

    // ==================================================================
    //  Apple taps
    // ==================================================================

    /** The prototype's cheerDino() - a squash-and-stretch bounce on the Dino. */
    private void cheerDino() {
        if (dinoSprite == null) {
            return;
        }
        dinoSprite.animate().cancel();
        dinoSprite.animate()
                .scaleX(1.08f).scaleY(0.92f).setDuration(90)
                .withEndAction(() -> {
                    if (dinoSprite != null) {
                        dinoSprite.animate()
                                .scaleX(1f).scaleY(1f)
                                .setInterpolator(new OvershootInterpolator())
                                .setDuration(260)
                                .start();
                    }
                })
                .start();
    }

    // ==================================================================
    //  Actions
    // ==================================================================

    private void wireActions(@NonNull View view) {
        View collect = view.findViewById(R.id.collectButton);
        if (collect != null) {
            collect.setOnClickListener(v -> triggerHarvest());
        }
        View launch = view.findViewById(R.id.launchButton);
        if (launch != null) {
            launch.setOnClickListener(v -> ScreenNav.showDashboard(this));
        }
        View skip = view.findViewById(R.id.skipButton);
        if (skip != null) {
            skip.setOnClickListener(v -> skipHarvest());
        }
    }

    /**
     * Port of triggerHarvest(): apples fall staggered 120ms apart, the Dino bounces at
     * 400ms, then every counter updates at 800ms and the toast shows for 2.4s. A second
     * tap is a plain navigation to the dashboard.
     */
    private void triggerHarvest() {
        if (collected) {
            ScreenNav.showDashboard(this);
            return;
        }
        collected = true;

        // Each apple grown on the tree drops toward the basket, staggered 120ms apart so
        // the harvest reads as a sequence rather than one jump.
        if (appleContainer != null) {
            for (int i = 0; i < appleContainer.getChildCount(); i++) {
                View apple = appleContainer.getChildAt(i);

                ObjectAnimator fall = ObjectAnimator.ofFloat(apple, View.TRANSLATION_Y, 0f, 140f);
                fall.setDuration(850);
                fall.setStartDelay(i * 120L);
                fall.setInterpolator(new AccelerateInterpolator());

                ObjectAnimator fade = ObjectAnimator.ofFloat(apple, View.ALPHA, 1f, 0f);
                fade.setDuration(850);
                fade.setStartDelay(i * 120L);

                AnimatorSet set = new AnimatorSet();
                set.playTogether(fall, fade);
                set.start();
            }
        }

        if (dinoSprite != null) {
            dinoSprite.postDelayed(() -> {
                if (dinoSprite == null) {
                    return;
                }
                dinoSprite.animate().cancel();
                dinoSprite.animate()
                        .translationY(-16f).scaleX(1.08f).scaleY(0.92f).setDuration(180)
                        .withEndAction(() -> {
                            if (dinoSprite != null) {
                                dinoSprite.animate()
                                        .translationY(0f).scaleX(1f).scaleY(1f)
                                        .setInterpolator(new OvershootInterpolator())
                                        .setDuration(320)
                                        .start();
                            }
                        })
                        .start();
            }, 400);
        }

        if (getView() != null) {
            getView().postDelayed(this::applyCollectedState, 800);
        }
    }

    private void applyCollectedState() {
        if (!isAdded() || getView() == null) {
            return;
        }

        int total = STARTING_STASH + APPLES_GROWN;
        renderCounters(total);

        if (collectButton != null) {
            collectButton.setText(R.string.harvest_start_day);
        }

        // The tree itself is a fixed blank vector and is deliberately left alone here.
        // Its apples were animated away in triggerHarvest(), so it is already bare again -
        // swapping in a "sparse" tree sprite would draw a second, contradictory crop.

        showToast(total);
    }

    private void showToast(int total) {
        if (harvestToast == null) {
            return;
        }
        TextView title = harvestToast.findViewById(R.id.toastTitle);
        TextView sub = harvestToast.findViewById(R.id.toastSub);
        if (title != null) {
            title.setText(getString(R.string.harvest_toast_title, APPLES_GROWN));
        }
        if (sub != null) {
            sub.setText(getString(R.string.harvest_toast_sub, total));
        }

        harvestToast.setVisibility(View.VISIBLE);
        harvestToast.setAlpha(0f);
        harvestToast.setTranslationY(80f);
        harvestToast.animate().alpha(1f).translationY(0f).setDuration(250).start();

        harvestToast.postDelayed(() -> {
            if (harvestToast == null) {
                return;
            }
            harvestToast.animate().alpha(0f).translationY(80f).setDuration(250)
                    .withEndAction(() -> {
                        if (harvestToast != null) {
                            harvestToast.setVisibility(View.GONE);
                        }
                    }).start();
        }, 2400);
    }

    /** The prototype's skipHarvest() - confirm, then go to the dashboard. */
    private void skipHarvest() {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.harvest_skip_confirm)
                .setPositiveButton(R.string.harvest_skip,
                        (dialog, which) -> ScreenNav.showDashboard(this))
                .setNegativeButton(R.string.addtask_cancel, null)
                .show();
    }
}

