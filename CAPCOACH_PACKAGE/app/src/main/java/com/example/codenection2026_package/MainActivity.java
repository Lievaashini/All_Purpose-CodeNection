package com.example.codenection2026_package;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.codenection2026_package.ui.onboarding.HardLimitsFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;

/**
 * Single-Activity host for CapCoach.
 *
 * <p>Keeps this project's existing edge-to-edge setup (transparent system bars plus the
 * window-insets listener) and adds the fragment container that hosts each screen.
 *
 * <p>Routing:
 * <pre>
 *   first launch       -> OnboardingFragment  (Screen 1)
 *   already onboarded  -> HardLimitsFragment  (Screen 2)
 * </pre>
 *
 * <p><b>To reset during testing:</b> uninstall the app or clear its storage, otherwise it
 * keeps opening straight into Screen 2.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Edge-to-edge: pad the root so nothing sits under the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            Fragment start = OnboardingPrefs.isOnboardingComplete(this)
                    ? new HardLimitsFragment()
                    : new OnboardingFragment();

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_container, start)
                    .commit();
        }
    }
}
