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
    private static final int APPLES_ON_TREE = 4;
    private static final int FEAST_GOAL = 20;
    private static final int STREAK_DAY = 4;

    private static final int[] APPLE_IDS = {R.id.apple1, R.id.apple2, R.id.apple3, R.id.apple4};
    private static final int[] APPLE_DOT_IDS = {R.id.apple1Dot, R.id.apple2Dot, R.id.apple3Dot, R.id.apple4Dot};

    private TextView basketLabel;
    private TextView stashSubtitle;
    private TextView progressPercentage;
    private TextView progressRatio;
    private TextView dinoChatBubble;
    private TextView collectButton;
    private View feastProgressBar;
    private View harvestToast;
    private ImageView appleTree;
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

        appleTree = view.findViewById(R.id.appleTree);
        dinoSprite = view.findViewById(R.id.dinoSprite);
        basketLabel = view.findViewById(R.id.basketLabel);
        stashSubtitle = view.findViewById(R.id.stashSubtitle);
        progressPercentage = view.findViewById(R.id.progressPercentage);
        progressRatio = view.findViewById(R.id.progressRatio);
        dinoChatBubble = view.findViewById(R.id.dinoChatBubble);
        feastProgressBar = view.findViewById(R.id.feastProgressBar);
        harvestToast = view.findViewById(R.id.harvestToast);
        collectButton = view.findViewById(R.id.collectButton);

        TextView streakText = view.findViewById(R.id.streakText);
        if (streakText != null) {
            streakText.setText(getString(R.string.harvest_streak_badge, STREAK_DAY));
        }
        TextView tooltipBadge = view.findViewById(R.id.tooltipBadge);
        if (tooltipBadge != null) {
            tooltipBadge.setText(getString(R.string.harvest_tooltip_badge, APPLES_ON_TREE));
        }
        TextView milestoneMid = view.findViewById(R.id.milestoneMid);
        if (milestoneMid != null) {
            milestoneMid.setText(getString(R.string.harvest_milestone_mid, FEAST_GOAL / 2));
        }
        TextView milestoneGoal = view.findViewById(R.id.milestoneGoal);
        if (milestoneGoal != null) {
            milestoneGoal.setText(getString(R.string.harvest_milestone_goal, FEAST_GOAL));
        }

        // Glide, not setImageResource - these assets are animated GIFs.
        if (isAdded()) {
            Glide.with(this).load(R.drawable.apple_tree_full).into(appleTree);
            Glide.with(this).load(R.drawable.dino_happy).into(dinoSprite);
        }

        renderCounters(STARTING_STASH);
        wireApples();
        wireActions(view);
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

    private void wireApples() {
        for (int i = 0; i < APPLE_IDS.length; i++) {
            View apple = getView() == null ? null : getView().findViewById(APPLE_IDS[i]);
            if (apple != null) {
                apple.setOnClickListener(v -> cheerDino());
            }
        }
    }

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

        for (int i = 0; i < APPLE_DOT_IDS.length; i++) {
            View dot = getView() == null ? null : getView().findViewById(APPLE_DOT_IDS[i]);
            if (dot == null) {
                continue;
            }

            ObjectAnimator fall = ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, 140f);
            fall.setDuration(850);
            fall.setStartDelay(i * 120L);
            fall.setInterpolator(new AccelerateInterpolator());

            ObjectAnimator fade = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0f);
            fade.setDuration(850);
            fade.setStartDelay(i * 120L);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(fall, fade);
            set.start();
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

        int total = STARTING_STASH + APPLES_ON_TREE;
        renderCounters(total);

        if (collectButton != null) {
            collectButton.setText(R.string.harvest_start_day);
        }

        // The tree sheds its crop, so swap to the sparser animated sprite.
        if (appleTree != null) {
            Glide.with(this).load(R.drawable.apple_tree_sparse).into(appleTree);
        }

        showToast(total);
    }

    private void showToast(int total) {
        if (harvestToast == null) {
            return;
        }
        TextView title = harvestToast.findViewById(R.id.toastTitle);
        TextView sub = harvestToast.findViewById(R.id.toastSub);
        if (title != null) {
            title.setText(getString(R.string.harvest_toast_title, APPLES_ON_TREE));
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