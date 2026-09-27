package com.example.codenection2026_package.ui.harvest;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.codenection2026_package.R;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.example.codenection2026_package.ui.shell.ScreenNav;

import java.util.Random;

/**
 * SCREEN 7 - WEEKLY FEAST. Port of Prototype/feast.html.
 *
 * <p>Sunday's reward loop: tap apples one at a time (or all at once) and watch the Dino's
 * satiety meter fill.
 *
 * <p>What the prototype's animation engine does, and what happened to it here:
 * <ul>
 *   <li>openDinoMouth / closeDinoMouth swap two SVG groups to give the Dino an open and a
 *       closed mouth. The Dino is now an animated GIF, so those states come from the
 *       sprite itself and the swap becomes a lean-forward transform.</li>
 *   <li>spawnFlyingApple appends a div and lets a CSS keyframe throw it at the Dino.
 *       Reproduced with an ObjectAnimator on a temporary View.</li>
 *   <li>dino-chewing / dino-idle are CSS classes, reproduced as squash-and-stretch.</li>
 *   <li>createCrumbs and createFloatingXP spawn short-lived particles; both are kept,
 *       because the "+25 XP" feedback is what makes the interaction legible.</li>
 * </ul>
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public class FeastFragment extends Fragment {

    /** Sunday is day 7 of the streak, per the prototype pill. */
    private static final int FEAST_DAY = 7;
    /** The prototype's harvest total for the week. */
    private static final int TOTAL_APPLES = 16;

    private int remainingApples = TOTAL_APPLES;
    private int eatenApples = 0;
    private boolean isEating = false;

    private final Random random = new Random();
    private View feastDino;
    private View dinoMouthClosed;
    private View dinoMouthOpen;
    private TextView dinoBubble;
    private TextView applesRemainingCount;
    private TextView satietyPercentage;
    private TextView progressText;
    private View progressBar;
    private TextView feastAllButton;
    private View feastToast;
    private TextView feastToastText;
    private LinearLayout appleGrid;
    private ViewGroup dinoStage;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feast, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);

        feastDino = view.findViewById(R.id.feastDino);
        dinoMouthClosed = view.findViewById(R.id.dinoMouthClosed);
        dinoMouthOpen = view.findViewById(R.id.dinoMouthOpen);
        dinoBubble = view.findViewById(R.id.dinoBubble);
        applesRemainingCount = view.findViewById(R.id.applesRemainingCount);
        satietyPercentage = view.findViewById(R.id.satietyPercentage);
        progressText = view.findViewById(R.id.progressText);
        progressBar = view.findViewById(R.id.feastProgressBar);
        appleGrid = view.findViewById(R.id.appleGrid);
        feastToast = view.findViewById(R.id.feastToast);
        feastToastText = view.findViewById(R.id.feastToastText);
        feastAllButton = view.findViewById(R.id.feastAllButton);

        View stage = view.findViewById(R.id.dinoStage);
        if (stage instanceof ViewGroup) {
            dinoStage = (ViewGroup) stage;
        }

        TextView pill = view.findViewById(R.id.feastPill);
        if (pill != null) {
            pill.setText(getString(R.string.feast_pill, FEAST_DAY));
        }
        TextView totalLabel = view.findViewById(R.id.applesTotalLabel);
        if (totalLabel != null) {
            totalLabel.setText(getString(R.string.feast_stash_total, TOTAL_APPLES));
        }
        TextView subtitle = view.findViewById(R.id.feastSubtitle);
        if (subtitle != null) {
            subtitle.setText(getString(R.string.feast_subtitle, TOTAL_APPLES));
        }

        buildAppleTray();
        wireActions(view);
        updateCounters();
    }

    // ==================================================================
    //  Apple tray
    // ==================================================================

    /** Four quick-feed buttons, mirroring the prototype's four apple buttons. */
    private void buildAppleTray() {
        if (appleGrid == null) {
            return;
        }
        appleGrid.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        String[] labels = {
                getString(R.string.feast_apple_cs101),
                getString(R.string.feast_apple_shift),
                getString(R.string.feast_apple_chem),
                getString(R.string.feast_apple_study)
        };

        for (String label : labels) {
            View button = inflater.inflate(R.layout.item_apple_button, appleGrid, false);
            TextView labelView = button.findViewById(R.id.appleButtonLabel);
            if (labelView != null) {
                labelView.setText(label);
            }
            button.setOnClickListener(v -> feedSingleApple(button));
            appleGrid.addView(button);
        }
    }

    // ==================================================================
    //  Actions
    // ==================================================================

    private void wireActions(@NonNull View view) {
        View close = view.findViewById(R.id.closeButton);
        if (close != null) {
            close.setOnClickListener(v -> ScreenNav.showDashboard(this));
        }
        View done = view.findViewById(R.id.doneButton);
        if (done != null) {
            done.setOnClickListener(v -> ScreenNav.showDashboard(this));
        }
        if (feastAllButton != null) {
            feastAllButton.setOnClickListener(v -> feedAllApples());
        }
        // Tapping the Dino itself feeds it, then becomes a hug once the basket is empty.
        if (feastDino != null) {
            feastDino.setOnClickListener(v -> quickFeedDino());
        }
    }

    /**
     * Port of feedSingleApple(). Runs the full chain: lean in, throw the apple, scatter
     * crumbs, float the XP, tick the counters, then chew and reset.
     */
    private void feedSingleApple(@Nullable View button) {
        if (isEating || remainingApples <= 0) {
            return;
        }
        isEating = true;

        if (button != null) {
            button.setEnabled(false);
            button.setAlpha(0.3f);
        }

        leanIn();
        openMouth();
        spawnFlyingApple(() -> {
            createCrumbs();
            createFloatingXp("+25 XP");

            remainingApples--;
            eatenApples++;
            updateCounters();

            closeMouth();
            chew(() -> {
                if (button != null) {
                    button.setEnabled(true);
                    button.setAlpha(1f);
                }
                isEating = false;
                if (remainingApples == 0) {
                    triggerFullFeastCelebration();
                }
            });

            if (dinoBubble != null) {
                dinoBubble.setText(randomHappyQuote());
            }
        });
    }

    private void quickFeedDino() {
        if (remainingApples > 0) {
            feedSingleApple(null);
        } else {
            triggerDinoHug();
        }
    }

    /**
     * Port of feedAllApples(): one apple every 90ms, then a single celebration. isEating
     * stays true for the whole cascade so taps cannot interleave.
     */
    private void feedAllApples() {
        if (isEating) {
            return;
        }
        if (remainingApples <= 0) {
            showToast(getString(R.string.feast_full_toast));
            return;
        }

        isEating = true;
        if (dinoBubble != null) {
            dinoBubble.setText(R.string.feast_bubble_all);
        }
        leanIn();
        openMouth();

        final int count = remainingApples;
        if (dinoStage != null) {
            for (int i = 0; i < count; i++) {
                dinoStage.postDelayed(() -> spawnFlyingApple(() -> {
                    createCrumbs();
                    remainingApples--;
                    eatenApples++;
                    updateCounters();
                }), i * 90L);
            }

            dinoStage.postDelayed(() -> {
                createFloatingXp("+400 XP MAX");
                closeMouth();
                chew(() -> {
                    isEating = false;
                    if (remainingApples == 0) {
                        triggerFullFeastCelebration();
                    }
                });
            }, (count * 90L) + 450L);
        } else {
            isEating = false;
        }
    }

    // ==================================================================
    //  Animation helpers
    // ==================================================================

    /** Squash-and-stretch "lean in" as the Dino anticipates the apple. */
    private void leanIn() {
        if (feastDino == null) {
            return;
        }
        feastDino.animate().cancel();
        feastDino.animate()
                .translationY(-6f)
                .scaleX(1.04f).scaleY(1.02f)
                .setDuration(150)
                .start();
    }

    /**
     * Port of the prototype's openDinoMouth(): hides the closed-mouth layer and reveals
     * the wide open one, so the Dino visibly opens up to catch the incoming apple.
     *
     * <p>This is why the Dino is drawn as vector layers rather than the animated GIF - an
     * image swap is the only way to change its mouth.
     */
    private void openMouth() {
        if (dinoMouthClosed != null) {
            dinoMouthClosed.setVisibility(View.INVISIBLE);
        }
        if (dinoMouthOpen != null) {
            dinoMouthOpen.setVisibility(View.VISIBLE);
        }
    }

    /** Port of closeDinoMouth(): restores the closed smile after a chew. */
    private void closeMouth() {
        if (dinoMouthOpen != null) {
            dinoMouthOpen.setVisibility(View.INVISIBLE);
        }
        if (dinoMouthClosed != null) {
            dinoMouthClosed.setVisibility(View.VISIBLE);
        }
    }

    /** Port of the dino-chewing keyframe: three quick squashes then a settle. */
    private void chew(@Nullable Runnable onEnd) {
        if (feastDino == null) {
            if (onEnd != null) {
                onEnd.run();
            }
            return;
        }
        feastDino.animate().cancel();

        final int[] frames = {0};
        final Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (feastDino == null) {
                return;
            }
            boolean squash = frames[0] % 2 == 0;
            feastDino.animate()
                    .scaleX(squash ? 1.08f : 0.96f)
                    .scaleY(squash ? 0.92f : 1.06f)
                    .setDuration(130)
                    .withEndAction(() -> {
                        frames[0]++;
                        if (frames[0] < 6) {
                            step[0].run();
                        } else if (feastDino != null) {
                            feastDino.animate().translationY(0f)
                                    .scaleX(1f).scaleY(1f)
                                    .setInterpolator(new OvershootInterpolator())
                                    .setDuration(220)
                                    .withEndAction(onEnd)
                                    .start();
                        }
                    })
                    .start();
        };
        step[0].run();
    }

    /**
     * Port of spawnFlyingApple(): a throwaway apple View that arcs up to the Dino, then
     * removes itself and fires the callback.
     */
    private void spawnFlyingApple(@Nullable Runnable onArrive) {
        if (dinoStage == null) {
            if (onArrive != null) {
                onArrive.run();
            }
            return;
        }
        final ViewGroup stage = dinoStage;
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(24 * density);

        final View apple = new View(requireContext());
        apple.setBackgroundResource(R.drawable.bg_apple);
        android.widget.FrameLayout.LayoutParams params =
                new android.widget.FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        stage.addView(apple, params);

        ObjectAnimator up = ObjectAnimator.ofFloat(apple, View.TRANSLATION_Y, 0f, -size * 6f);
        ObjectAnimator spin = ObjectAnimator.ofFloat(apple, View.ROTATION, 0f, 360f);
        ObjectAnimator fade = ObjectAnimator.ofFloat(apple, View.ALPHA, 1f, 0f);
        fade.setStartDelay(380);
        fade.setDuration(170);

        up.setDuration(550);
        up.setInterpolator(new AccelerateInterpolator());
        spin.setDuration(550);

        up.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stage.removeView(apple);
                if (onArrive != null) {
                    onArrive.run();
                }
            }
        });

        up.start();
        spin.start();
        fade.start();
    }

    /** Port of createCrumbs(): seven short-lived coloured specks. */
    private void createCrumbs() {
        if (dinoStage == null) {
            return;
        }
        final ViewGroup stage = dinoStage;
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < 7; i++) {
            final View crumb = new View(requireContext());
            int px = Math.round((3 + random.nextInt(5)) * density);
            crumb.setBackgroundColor(random.nextFloat() > 0.3f
                    ? ContextCompatColor(R.color.apple_red)
                    : ContextCompatColor(R.color.brand_gold));

            android.widget.FrameLayout.LayoutParams params =
                    new android.widget.FrameLayout.LayoutParams(px, px);
            params.gravity = Gravity.CENTER;
            stage.addView(crumb, params);

            float tx = (random.nextFloat() - 0.3f) * 50f * density;
            float ty = (random.nextFloat() - 0.5f) * 40f * density;

            ObjectAnimator x = ObjectAnimator.ofFloat(crumb, View.TRANSLATION_X, 0f, tx);
            ObjectAnimator y = ObjectAnimator.ofFloat(crumb, View.TRANSLATION_Y, 0f, ty);
            ObjectAnimator fade = ObjectAnimator.ofFloat(crumb, View.ALPHA, 1f, 0f);
            x.setDuration(500);
            y.setDuration(500);
            fade.setDuration(500);

            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    stage.removeView(crumb);
                }
            });
            x.start();
            y.start();
            fade.start();
        }
    }

    private int ContextCompatColor(int res) {
        return androidx.core.content.ContextCompat.getColor(requireContext(), res);
    }

    /** Port of createFloatingXP(): the "+25 XP" text that floats up and fades. */
    private void createFloatingXp(@NonNull String text) {
        if (dinoStage == null) {
            return;
        }
        final ViewGroup stage = dinoStage;
        float density = getResources().getDisplayMetrics().density;

        final TextView xp = new TextView(requireContext());
        xp.setText(text);
        xp.setTextColor(ContextCompatColor(R.color.brand_mint));
        xp.setTextSize(12f);
        xp.setTypeface(null, android.graphics.Typeface.BOLD);

        android.widget.FrameLayout.LayoutParams params =
                new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        stage.addView(xp, params);

        ObjectAnimator rise = ObjectAnimator.ofFloat(xp, View.TRANSLATION_Y, 0f, -50f * density);
        ObjectAnimator fade = ObjectAnimator.ofFloat(xp, View.ALPHA, 0f, 1f, 1f, 0f);
        rise.setDuration(900);
        fade.setDuration(900);

        fade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stage.removeView(xp);
            }
        });
        rise.start();
        fade.start();
    }

    /** Port of triggerDinoHug(), for tapping a Dino with an empty basket. */
    private void triggerDinoHug() {
        if (dinoBubble != null) {
            dinoBubble.setText(R.string.feast_bubble_hug);
        }
        createFloatingXp("+1 LOVE");
        if (feastDino != null) {
            feastDino.animate().cancel();
            feastDino.animate().scaleX(1.15f).scaleY(1.15f).setDuration(160)
                    .withEndAction(() -> {
                        if (feastDino != null) {
                            feastDino.animate().scaleX(1f).scaleY(1f)
                                    .setDuration(200).start();
                        }
                    })
                    .start();
        }
    }

    /** Port of triggerFullFeastCelebration(). */
    private void triggerFullFeastCelebration() {
        if (dinoBubble != null) {
            dinoBubble.setText(R.string.feast_bubble_done);
        }
        showToast(getString(R.string.feast_complete_toast));
    }

    @NonNull
    private String randomHappyQuote() {
        int[] quotes = {
                R.string.feast_quote_1,
                R.string.feast_quote_2,
                R.string.feast_quote_3,
                R.string.feast_quote_4,
                R.string.feast_quote_5
        };
        return getString(quotes[random.nextInt(quotes.length)]);
    }

    // ==================================================================
    //  Counters
    // ==================================================================

    private void updateCounters() {
        if (applesRemainingCount != null) {
            applesRemainingCount.setText(String.valueOf(remainingApples));
        }

        int percent = Math.round(eatenApples * 100f / TOTAL_APPLES);
        if (satietyPercentage != null) {
            satietyPercentage.setText(percent + "%");
        }
        if (progressText != null) {
            progressText.setText(getString(R.string.feast_progress_text, eatenApples, TOTAL_APPLES));
        }
        setBarFraction(progressBar, percent / 100f);

        if (feastAllButton != null) {
            feastAllButton.setText(remainingApples > 0
                    ? getString(R.string.feast_feed_remaining, remainingApples)
                    : getString(R.string.feast_complete));
        }
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

    private void showToast(@NonNull String message) {
        if (feastToast == null) {
            return;
        }
        if (feastToastText != null) {
            feastToastText.setText(message);
        }
        feastToast.setVisibility(View.VISIBLE);
        feastToast.setAlpha(0f);
        feastToast.animate().alpha(1f).setDuration(220).start();

        feastToast.postDelayed(() -> {
            if (feastToast == null) {
                return;
            }
            feastToast.animate().alpha(0f).setDuration(220)
                    .withEndAction(() -> {
                        if (feastToast != null) {
                            feastToast.setVisibility(View.GONE);
                        }
                    }).start();
        }, 2500);
    }
}


