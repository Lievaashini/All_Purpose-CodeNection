package com.example.codenection2026_package;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.codenection2026_package.ui.dashboard.DashboardFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.shell.PopupGate;

public class MainActivity extends AppCompatActivity {

    /**
     * Whether the first screen is still being chosen.
     *
     * <p>The pop-up decision takes a database read, so the app spends a few milliseconds with
     * an empty container. An activity recreated in that window has nothing to restore and
     * would come back blank, so the flag travels with the instance state and the recreate
     * simply asks again.
     */
    private static final String STATE_START_SCREEN_PENDING = "start_screen_pending";

    private boolean startScreenPending;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // UI Teammate: Edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // UI Teammate: Fragment Routing
        // A recreate normally restores the screen the user was on, so routing only happens on
        // a real start - or on a recreate that was interrupted before anything was committed.
        boolean needsStartScreen = savedInstanceState == null
                || savedInstanceState.getBoolean(STATE_START_SCREEN_PENDING, false);
        if (needsStartScreen) {
            routeStartScreen();
        }
    }

    /**
     * Chooses the screen the app opens on.
     *
     * <p>An onboarded user opens on the dashboard - the app's home screen. This used to start
     * HardLimitsFragment (Screen 2 "Set Limits"), an ONBOARDING step, so every launch after the
     * first dropped the user back into setup and they had to press "Save &amp; Launch" again to
     * reach their own schedule. Screen 2 is still reachable deliberately, from Biometrics'
     * "Override Sensor Metric".
     *
     * <p>Before the dashboard, the day's pop-up gets its turn: the daily harvest on the first
     * open of a day, the weekly feast on the first open of a Monday, harvest first when Monday
     * owes both. Whether there is anything in the tray is a database question, so the answer
     * arrives a few milliseconds later - the container is left empty until then rather than
     * flashing the dashboard first. PopupGate hands back null for the rest of the week, which
     * is the normal path to the dashboard.
     */
    private void routeStartScreen() {
        if (!OnboardingPrefs.isOnboardingComplete(this)) {
            show(new OnboardingFragment());
            return;
        }

        startScreenPending = true;
        PopupGate.resolveAppOpenPopup(this, popup -> {
            startScreenPending = false;
            show(popup != null ? popup : new DashboardFragment());
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Saved from the live flag, so a recreate knows whether a screen ever arrived. Read
        // before the callback clears it, which is the case that matters: the commit is refused
        // while state is saved, and the recreate then routes again.
        outState.putBoolean(STATE_START_SCREEN_PENDING, startScreenPending);
    }

    /** Commits the first screen, unless the activity is already on its way out. */
    private void show(@NonNull Fragment screen) {
        FragmentManager fragments = getSupportFragmentManager();
        // The decision can land while the activity is in the background - the user pressed
        // home during those few milliseconds - and committing then would throw. It is not
        // lost: the flag above sends the recreate back through routeStartScreen().
        if (isFinishing() || isDestroyed() || fragments.isStateSaved()) {
            return;
        }
        fragments.beginTransaction()
                .replace(R.id.nav_host_container, screen)
                .commit();
    }
}
