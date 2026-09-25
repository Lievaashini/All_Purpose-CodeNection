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