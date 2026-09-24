package com.example.codenection2026_package.model;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.codenection2026_package.R;

/**
 * The three coaching tones from {@code onboarding.html}.
 *
 * <p>The tone changes the Dino's dialogue only - it is a presentation preference, so it
 * lives here rather than in the engine. Role 1 persists the chosen id on the
 * {@code User} entity as {@code coachingTone}.
 */
public enum ToneType {

    /** Bright, energetic, exclamation-heavy. Default selection in the prototype. */
    HYPE("HYPE", R.string.tone_hype_speech),

    /** Calm, gentle pacing. */
    CHILL("CHILL", R.string.tone_chill_speech),

    /** Terse telemetry, no encouragement. */
    PLAIN("PLAIN", R.string.tone_plain_speech);

    /** Value written to the database. Must match TEAM_HANDSHAKE.md section 5.1. */
    @NonNull
    public final String storageValue;

    /** Dialogue the Dino speaks when this tone is selected. */
    @StringRes
    public final int speechRes;

    ToneType(@NonNull String storageValue, @StringRes int speechRes) {
        this.storageValue = storageValue;
        this.speechRes = speechRes;
    }

    /** Parses a stored value, defaulting to {@link #HYPE} for anything unknown. */
    @NonNull
    public static ToneType fromStorage(String value) {
        if (value != null) {
            for (ToneType tone : values()) {
                if (tone.storageValue.equalsIgnoreCase(value)) {
                    return tone;
                }
            }
        }
        return HYPE;
    }
}
