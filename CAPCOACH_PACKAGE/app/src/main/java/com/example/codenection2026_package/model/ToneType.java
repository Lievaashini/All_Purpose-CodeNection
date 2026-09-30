package com.example.codenection2026_package.model;

import androidx.annotation.NonNull;

/**
 * The three coaching tones from {@code onboarding.html}.
 *
 * <p>The tone changes the Dino's dialogue only - it is a presentation preference, so it
 * lives here rather than in the engine. Role 1 persists the chosen id on the
 * {@code User} entity as {@code coachingTone}.
 *
 * <p>This enum is deliberately just an identity: it says WHICH tone, never what the tone
 * says. The wording lives in {@link CoachVoice}, which maps each dialogue slot to one string
 * per tone, so adding a line means touching the three {@code dino_*.xml} files and
 * {@code CoachVoice} rather than this class.
 */
public enum ToneType {

    /** Bright, energetic, exclamation-heavy. Default selection in the prototype. */
    HYPE("HYPE"),

    /** Calm, gentle pacing. */
    CHILL("CHILL"),

    /** Terse telemetry, no encouragement. */
    PLAIN("PLAIN");

    /** Value written to the database. Must match TEAM_HANDSHAKE.md section 5.1. */
    @NonNull
    public final String storageValue;

    ToneType(@NonNull String storageValue) {
        this.storageValue = storageValue;
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
