package com.example.codenection2026_package.ui.onboarding;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.example.codenection2026_package.R;
import com.google.android.material.card.MaterialCardView;

/**
 * Handles the theme toggle used on Screens 1 and 2.
 *
 * <p>The prototype swaps CSS classes at runtime. Android does it properly: persist the
 * choice, then let AppCompat rebuild the activity against the right resource set.
 * Bright colours live in {@code res/values-night/colors.xml}.
 */
public final class ThemeController {

    private ThemeController() {
    }

    /**
     * Wires a theme toggle button. Called from both {@code OnboardingFragment} and
     * {@code HardLimitsFragment} so the behaviour is identical on both screens.
     *
     * @param root      any view in the current screen, used to find the button and icon
     * @param buttonId  id of the clickable container
     * @param iconId    id of the icon inside it
     */
    public static void bind(@NonNull View root, @IdRes int buttonId, @IdRes int iconId) {
        View button = root.findViewById(buttonId);
        ImageView icon = root.findViewById(iconId);
        if (button == null) {
            return;
        }

        updateIcon(root.getContext(), icon);

        button.setOnClickListener(v -> {
            int next = isNightMode(root.getContext())
                    ? AppCompatDelegate.MODE_NIGHT_NO
                    : AppCompatDelegate.MODE_NIGHT_YES;
            AppCompatDelegate.setDefaultNightMode(next);
        });
    }

    /** @return true when the app is currently rendering the dark palette. */
    public static boolean isNightMode(@NonNull Context context) {
        int mode = AppCompatDelegate.getDefaultNightMode();
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            return true;
        }
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
            return false;
        }
        // MODE_NIGHT_FOLLOW_SYSTEM or UNSET: fall back to the system setting.
        int uiMode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static void updateIcon(@NonNull Context context, ImageView icon) {
        if (icon == null) {
            return;
        }
        // The icon advertises the theme you would switch TO, not the one you are in:
        //   bright showing -> tapping goes to night  -> show the moon
        //   night showing  -> tapping goes to bright -> show the sun
        @DrawableRes int res = isNightMode(context)
                ? R.drawable.ic_light_mode
                : R.drawable.ic_dark_mode;
        icon.setImageResource(res);
        icon.setImageTintList(
                ContextCompat.getColorStateList(context, R.color.brand_mint));
    }

    /**
     * Convenience for fragments that need the hosting activity.
     * Returns null when the fragment is detached, which is exactly when you must
     * not touch views.
     */
    public static Activity activityOrNull(@NonNull androidx.fragment.app.Fragment fragment) {
        return fragment.getActivity();
    }

    /** Small helper so callers do not repeat the cast when they just need a TextView. */
    public static void setTextOrHide(TextView view, CharSequence text) {
        if (view == null) {
            return;
        }
        if (text == null || text.length() == 0) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(text);
        }
    }

    /** Returns the card itself, or null when the include failed to inflate. */
    public static MaterialCardView findCard(View root, @IdRes int id) {
        View v = root.findViewById(id);
        return v instanceof MaterialCardView ? (MaterialCardView) v : null;
    }
}
