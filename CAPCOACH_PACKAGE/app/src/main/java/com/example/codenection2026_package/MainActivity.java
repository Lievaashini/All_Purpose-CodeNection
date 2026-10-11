package com.example.codenection2026_package;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.codenection2026_package.ui.dashboard.DashboardFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.shell.PopupGate;

public class MainActivity extends AppCompatActivity {

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
        if (savedInstanceState == null) {
            // An onboarded user opens on the dashboard - the app's home screen. This used to
            // start HardLimitsFragment (Screen 2 "Set Limits"), an ONBOARDING step, so every
            // launch after the first dropped the user back into setup and they had to press
            // "Save & Launch" again to reach their own schedule. Screen 2 is still reachable
            // deliberately, from Biometrics' "Override Sensor Metric".
            //
            // Before the dashboard, the day's pop-up gets its turn: the daily harvest on the
            // first open of a day, the weekly feast on the first open of a Monday, harvest
            // first when Monday owes both. PopupGate answers "nothing due" for the rest of
            // the week, which is the normal path straight to the dashboard.
            Fragment start;
            if (!OnboardingPrefs.isOnboardingComplete(this)) {
                start = new OnboardingFragment();
            } else {
                Fragment popup = PopupGate.claimPopupForAppOpen(this);
                start = popup != null ? popup : new DashboardFragment();
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_container, start)
                    .commit();
        }
    }

    /**
     * Catches the day changing while the app sat in the background.
     *
     * <p>{@link #onCreate} only covers a cold start, but a phone that was left on the
     * dashboard overnight returns to {@link #onStart} with the process - and yesterday's
     * pop-up markers - still in memory. Without this second check the user would have to
     * force-close the app to see the morning harvest.
     *
     * <p>Only fires from the dashboard, and only once per day, because
     * {@link PopupGate#claimPopupForAppOpen} spends the day's pop-up as it hands it over: a
     * screen the user is working on is never interrupted, and returning to the app later the
     * same day is silent.
     */
    @Override
    protected void onStart() {
        super.onStart();

        if (!OnboardingPrefs.isOnboardingComplete(this)) {
            return;
        }

        FragmentManager fragments = getSupportFragmentManager();
        // Committing after onSaveInstanceState would throw, and there is nothing to show on
        // a screen the user is already leaving.
        if (fragments.isStateSaved()) {
            return;
        }

        Fragment visible = fragments.findFragmentById(R.id.nav_host_container);
        if (!(visible instanceof DashboardFragment)) {
            return;
        }

        Fragment popup = PopupGate.claimPopupForAppOpen(this);
        if (popup == null) {
            return;
        }

        fragments.beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.nav_host_container, popup)
                .commit();
    }
}
