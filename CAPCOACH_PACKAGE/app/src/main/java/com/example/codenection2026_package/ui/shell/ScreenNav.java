package com.example.codenection2026_package.ui.shell;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
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

    public static void showDashboard(@NonNull Fragment from) {
        replace(from, new DashboardFragment(), "dashboard");
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