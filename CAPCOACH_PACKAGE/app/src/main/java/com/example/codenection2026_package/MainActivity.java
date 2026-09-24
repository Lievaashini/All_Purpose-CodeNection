package com.example.codenection2026_package;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.health.connect.client.PermissionController;

import com.example.codenection2026_package.ui.onboarding.HardLimitsFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;

import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private HealthConnectManager healthManager;

    // Member 4: Health Connect Permission Launcher
    private final ActivityResultLauncher<Set<String>> requestPermissionLauncher =
            registerForActivityResult(
                    PermissionController.createRequestPermissionResultContract(),
                    grantedPermissions -> {
                        if (grantedPermissions.containsAll(healthManager.getRequiredPermissions())) {
                            Log.d("CapCoach", "Health Connect Permissions Granted!");
                        } else {
                            Log.d("CapCoach", "Permissions Denied.");
                        }
                    }
            );

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

        // Member 4: Initialize and trigger Health Connect
        healthManager = new HealthConnectManager(this);
        if (healthManager.isClientAvailable()) {
            requestPermissionLauncher.launch(healthManager.getRequiredPermissions());
        }
    }
}