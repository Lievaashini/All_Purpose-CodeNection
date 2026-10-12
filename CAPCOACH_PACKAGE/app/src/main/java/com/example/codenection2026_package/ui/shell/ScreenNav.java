package com.example.codenection2026_package.ui.shell;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.codenection2026_package.R;
import com.example.codenection2026_package.ui.biometrics.BiometricsFragment;
import com.example.codenection2026_package.ui.dashboard.DashboardFragment;
import com.example.codenection2026_package.ui.harvest.DailyHarvestFragment;
import com.example.codenection2026_package.ui.harvest.FeastFragment;
import com.example.codenection2026_package.ui.onboarding.HardLimitsFragment;
import com.example.codenection2026_package.ui.settings.SettingsFragment;

/**
 * The one place that knows how to move between the top-level screens.
 *
 * <p>The prototype navigates with window.location.href = 'dashboard.html' - a full page
 * load, from anywhere, with no history stack. The Android equivalent is a fragment
 * transaction on R.id.nav_host_container, centralised here so no other screen has to
 * build one itself.
 *
 * <p>Every replace is added to the back stack, so the system Back button walks the user
 * back the way they came instead of dropping out of the app.
 *
 * <p>NEW FILE - additive only. It does not modify the onboarding flow: MainActivity,
 * OnboardingFragment and HardLimitsFragment keep their own navigation as written.
 */
public final class ScreenNav {

    /** Which bottom-navigation entry a screen belongs to. */
    public enum Tab {
        HOME,
        BIOMETRICS,
        SETTINGS
    }

    private ScreenNav() {
    }

    /**
     * Goes to the dashboard - or, when the screen being left is the daily harvest and the
     * weekly feast is still owed that day, to the feast instead.
     *
     * <p>The two pop-ups are a queue, not two separate screens: the user collects the apples
     * first and only then feeds them to the Dino, so on a Monday the feast never jumps in
     * front of the harvest. The dashboard is reached on the next navigation, from the feast's
     * own Done button.
     *
     * <p>The check is on the screen being left, not on how the user got there: a harvest
     * opened from the dashboard's button on a Monday chains to the feast exactly like the one
     * the app opened by itself. Both end at the same place, and requiring a flag to tell them
     * apart would only mean a button-opened Monday harvest silently swallowed the feast.
     *
     * <p>Every other caller of this method means the dashboard, exactly as before, so no
     * screen can be redirected by a pop-up that happens to be pending behind it.
     */
    public static void showDashboard(@NonNull Fragment from) {
        if (from instanceof DailyHarvestFragment) {
            Context context = from.getContext();
            Fragment queued = context == null ? null : PopupGate.weeklyPopup(context);
            if (queued != null) {
                replace(from, queued, "feast");
                return;
            }
        }
        replace(from, new DashboardFragment(), "dashboard");
    }

    /**
     * Opens whatever pop-up the day still owes, if the screen asking is still the one on
     * show. Called when the dashboard appears, which is what lets a harvest arrive on a
     * morning the app was left sitting on another screen.
     *
     * <p>The answer takes a database read, so the screen the user asked from may be gone by
     * the time it lands - they tapped Biometrics, or left the app. Rather than interrupting
     * whatever they are doing now, the pop-up is dropped, and because a pop-up is only marked
     * as seen once it is on screen ({@link PopupGate#markShown}), nothing is spent: the next
     * arrival at the dashboard is offered it again.
     *
     * <p>Does nothing when the day is owed nothing, which is the everyday case and costs one
     * prefs read; see {@link PopupGate#resolveAppOpenPopup}.
     */
    public static void showDuePopup(@NonNull Fragment from) {
        showDuePopup(from, null);
    }

    /**
     * As {@link #showDuePopup(Fragment)}, with something for the caller to do when the day owes
     * no pop-up at all.
     *
     * <p>Added for the dashboard, which has one other thing it may open on arrival of its own
     * accord - the Rebalance offer - and must not compete with a pop-up for the same moment.
     * The follow-up runs only on a genuine "nothing to show", and only while the asking screen is
     * still the one on screen: when a pop-up was owed and then dropped because the user moved on,
     * the caller hears nothing, exactly as before.
     *
     * <p><b>Why a callback and not a check of the caller's own.</b> "Nothing to show" is not the
     * same question as {@link PopupGate#isDailyDue}. A morning whose harvest would have been empty
     * is still due all day - nothing was marked as seen, deliberately - so anything gated on that
     * flag would wait for a pop-up that is never coming and never run on exactly those days. The
     * answer handed over here is the same one the pop-up itself was decided on.
     *
     * @param onNothingDue run on the main thread when there is nothing to open, or null
     */
    public static void showDuePopup(@NonNull Fragment from, @Nullable Runnable onNothingDue) {
        final Context context = from.getContext();
        if (context == null) {
            return;
        }

        PopupGate.resolveAppOpenPopup(context, popup -> {
            // The answer takes a database read, so the screen the user asked from may be gone by
            // the time it lands - they tapped Biometrics, or left the app. Rather than
            // interrupting whatever they are doing now, both the pop-up and the caller's
            // follow-up are dropped, and because a pop-up is only marked as seen once it is on
            // screen (PopupGate.markShown), nothing is spent by that.
            if (!isStillOnScreen(from)) {
                return;
            }
            if (popup == null) {
                if (onNothingDue != null) {
                    onNothingDue.run();
                }
                return;
            }
            FragmentManager fragments = from.getParentFragmentManager();
            String tag = popup instanceof FeastFragment ? "feast" : "daily_harvest";
            fragments.beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.nav_host_container, popup, tag)
                    .addToBackStack(tag)
                    .commit();
        });
    }

    /**
     * True while {@code from} is still the screen in the container and its state has not been
     * saved.
     *
     * <p>Anything opened against a screen that fails this would either land on top of a screen the
     * user has already moved on to, or be committed after the state was saved - which is a
     * transaction the framework refuses.
     */
    private static boolean isStillOnScreen(@NonNull Fragment from) {
        if (!from.isAdded()) {
            return false;
        }
        FragmentManager fragments = from.getParentFragmentManager();
        return fragments.findFragmentById(R.id.nav_host_container) == from
                && !fragments.isStateSaved();
    }

    public static void showBiometrics(@NonNull Fragment from) {
        replace(from, new BiometricsFragment(), "biometrics");
    }

    public static void showSettings(@NonNull Fragment from) {
        replace(from, new SettingsFragment(), "settings");
    }

    public static void showDailyHarvest(@NonNull Fragment from) {
        replace(from, new DailyHarvestFragment(), "daily_harvest");
    }

    public static void showFeast(@NonNull Fragment from) {
        replace(from, new FeastFragment(), "feast");
    }

    /**
     * Opens the Hard Limits screen (Screen 2) so the user can re-edit the study / work /
     * co-curricular ceilings.
     *
     * <p>Reachable from the biometrics screen's "Override Sensor Metric" button: when a
     * biometric reading is wrong, the manual override lives on the limits screen, not on
     * today's schedule. This reuses the existing {@link HardLimitsFragment} unchanged -
     * no edits to the onboarding package were needed.
     */
    public static void showHardLimits(@NonNull Fragment from) {
        replace(from, new HardLimitsFragment(), "hard_limits");
    }

    /**
     * Back without popping the whole activity: pops the fragment back stack when there is
     * something to pop, otherwise falls through to the dashboard so the user is never
     * stranded on an empty container.
     */
    public static void back(@NonNull Fragment from) {
        if (from.getParentFragmentManager().getBackStackEntryCount() > 0) {
            from.getParentFragmentManager().popBackStack();
        } else {
            showDashboard(from);
        }
    }

    private static void replace(@NonNull Fragment from, @NonNull Fragment next, @NonNull String tag) {
        if (!from.isAdded()) {
            return;
        }
        from.getParentFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.nav_host_container, next, tag)
                .addToBackStack(tag)
                .commit();
    }

    /**
     * Wires the shared include_bottom_nav bar for a screen.
     *
     * @param host    fragment used to start the transaction
     * @param root    any view in the host screen
     * @param current which tab should render as selected
     */
    public static void bindNav(@NonNull Fragment host, @NonNull View root, @NonNull Tab current) {
        tintTab(root, R.id.navHomeIcon, R.id.navHomeLabel, current == Tab.HOME);
        tintTab(root, R.id.navBiometricsIcon, R.id.navBiometricsLabel, current == Tab.BIOMETRICS);
        tintTab(root, R.id.navSettingsIcon, R.id.navSettingsLabel, current == Tab.SETTINGS);

        click(root, R.id.navHome, () -> {
            if (current != Tab.HOME) {
                showDashboard(host);
            }
        });
        click(root, R.id.navBiometrics, () -> {
            if (current != Tab.BIOMETRICS) {
                showBiometrics(host);
            }
        });
        click(root, R.id.navSettings, () -> {
            if (current != Tab.SETTINGS) {
                showSettings(host);
            }
        });
    }

    private static void tintTab(@NonNull View root,
                                @IdRes int iconId,
                                @IdRes int labelId,
                                boolean selected) {
        @ColorRes int colorRes = selected ? R.color.brand_mint : R.color.text_dim_dark;
        int color = ContextCompat.getColor(root.getContext(), colorRes);

        ImageView icon = root.findViewById(iconId);
        if (icon != null) {
            icon.setImageTintList(ColorStateList.valueOf(color));
        }
        TextView label = root.findViewById(labelId);
        if (label != null) {
            label.setTextColor(color);
            label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private static void click(@NonNull View root, @IdRes int id, @NonNull Runnable action) {
        View target = root.findViewById(id);
        if (target != null) {
            target.setOnClickListener(v -> action.run());
        }
    }
}