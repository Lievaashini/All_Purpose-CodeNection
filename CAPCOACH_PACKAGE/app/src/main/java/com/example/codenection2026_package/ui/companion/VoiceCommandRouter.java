package com.example.codenection2026_package.ui.companion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The voice-command table: the one place that turns a recognised transcript into the screen
 * the user asked for.
 *
 * <p>This table used to live twice - once in {@link CompanionFragment} and once in
 * {@link VoiceDinoDialogFragment} - and the two copies had already drifted apart. The
 * onboarding popup and the full voice screen now ask this class the same question and differ
 * only in <i>how</i> they act on the answer, so the two entry points cannot disagree about
 * what "view my sleep" means.
 *
 * <p><b>Matching.</b> The prototype matched word-boundary regexes, and so does this class.
 * A plain {@code contains} test is not good enough once the word lists grow, because short
 * commands then mean the wrong thing: {@code contains("back")} also fires on "backlog",
 * {@code contains("cap")} on "capstone", {@code contains("limit")} on "unlimited", and
 * {@code contains("home")} on "homework". A keyword here matches only when it starts a
 * word, so {@code "limit"} still matches "limit", "limits" and "limiting" - and correctly
 * ignores "unlimited".
 *
 * <p><b>Ordering.</b> The first branch that matches wins, so the order is deliberate:
 * <ol>
 *   <li>Add task first, because it opens a sheet rather than a screen and because its words
 *       overlap with the dashboard's ("add my homework" mentions "home").</li>
 *   <li>Then the specific screen words: sleep/health, then the metric screen, then
 *       settings - "metric" is claimed by the limits screen, so a metric the user frames as
 *       a reading ("view my sleep") is caught by the sleep branch above it.</li>
 *   <li>Then the pop-ups, then the dashboard and schedule, which are the catch-all phrasing
 *       the Dino itself suggests ("Hey Dino, reschedule my shift").</li>
 *   <li>Back / close last, so "close the settings" opens Settings rather than closing the
 *       screen out from under the user.</li>
 * </ol>
 *
 * <p>NEW FILE - additive. The two voice screens were modified only to call it.
 */
public final class VoiceCommandRouter {

    /** What the user asked for. {@link #NONE} means the app has no answer for it. */
    public enum Command {
        ADD_TASK,
        BIOMETRICS,
        HARD_LIMITS,
        SETTINGS,
        DAILY_HARVEST,
        FEAST,
        DASHBOARD,
        BACK,
        NONE
    }

    // ------------------------------------------------------------------
    //  The table
    // ------------------------------------------------------------------

    /**
     * "add a task", "create revision notes", "log my homework" - an action verb followed by
     * something that is a task. Written as a pattern rather than a word list so the task
     * does not have to be named exactly: "add my chemistry homework" works as well as
     * "add task".
     */
    private static final Pattern ADD_VERB_THEN_TASK = Pattern.compile(
            "\\b(add|create|log)\\b[^.!?]{0,80}?\\b(task|homework|assignment|to-?do"
                    + "|reminder|deadline|chore|errand|exam|quiz|test|project|essay|paper"
                    + "|revision|study block|appointment|meeting|class)\\b");

    /**
     * "new task", "new reminder".
     *
     * <p>"new" is an adjective as often as it is a command ("the new test was hard"), so it
     * only counts when it is followed by a word that is unambiguously a task.
     */
    private static final Pattern NEW_THEN_TASK = Pattern.compile(
            "\\bnew\\b[^.!?]{0,80}?\\b(task|homework|assignment|to-?do|reminder"
                    + "|deadline|chore|errand)\\b");

    /**
     * "back", "close", "cancel".
     *
     * <p>Whole words, not word starts: {@code \bback} would also fire on "backlog",
     * "background" and "backpack", all of which are ordinary words in this app's sentences.
     */
    private static final Pattern BACK = Pattern.compile(
            "\\b(back|close|cancel|dismiss|exit|never ?mind)\\b");

    /**
     * The biometrics screen: sleep, heart, recovery and every other sensor reading.
     *
     * <p>"sensor" is here, so "override sensor metric" opens biometrics - which is where the
     * "Override Sensor Metric" button lives, so the user is one tap from the override rather
     * than dropped somewhere unrelated.
     */
    private static final String[] BIOMETRICS = {
            "biometric", "health", "sleep", "slept", "hrv", "heart", "recovery", "readiness",
            "steps", "step count", "stress", "vitals", "sensor", "body battery",
            "circadian", "fatigue", "energy level",
    };

    /**
     * Screen 2 - hard limits, the study / work / co-curricular ceilings.
     *
     * <p>"metric" lands here on purpose: when a sensor reading is wrong, the manual override
     * is a ceiling on this screen, which is why the biometrics screen's button is labelled
     * "Override Sensor Metric". The sleep/health branch is checked first, so "view my sleep"
     * still opens biometrics - only a metric on its own comes here.
     */
    private static final String[] HARD_LIMITS = {
            "metric", "limit", "ceiling", "capacity", "baseline", "maximum", "max hours",
            "study hours", "work hours", "hour cap", "cocurricular", "co-curricular",
    };

    /** Settings: coaching tone, theme, privacy, the linked account. */
    private static final String[] SETTINGS = {
            "setting", "privacy", "preference", "tone", "theme", "dark mode", "light mode",
            "appearance", "profile", "account", "notification", "permission", "share",
            "my data",
    };

    /** The daily pop-up / harvest. */
    private static final String[] DAILY_HARVEST = {
            "daily pop-up", "daily pop up", "daily harvest", "harvest", "daily review",
            "pop-up", "pop up",
    };

    /** The weekly pop-up / feast. */
    private static final String[] FEAST = {
            "weekly pop-up", "weekly pop up", "weekly review", "feast",
    };

    /** The dashboard, which is also where the schedule and the task list live. */
    private static final String[] DASHBOARD = {
            "dashboard", "home", "main page", "main screen", "schedule", "reschedule",
            "shift", "calendar", "roster", "timetable", "agenda", "my day",
            "today's plan", "my plan", "task list", "todo list", "to-do list",
    };

    private VoiceCommandRouter() {
    }

    /**
     * Resolves what the user asked for.
     *
     * @param transcript the recogniser's best guess, in any casing, possibly null
     * @return the first matching command, or {@link Command#NONE} when nothing matches - the
     *         caller then leaves the user where they are and shows the transcript back.
     */
    @NonNull
    public static Command route(@Nullable String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            return Command.NONE;
        }

        String q = transcript.toLowerCase(Locale.US);

        if (ADD_VERB_THEN_TASK.matcher(q).find() || NEW_THEN_TASK.matcher(q).find()) {
            return Command.ADD_TASK;
        }
        if (BACK.matcher(q).find()) {
            return Command.BACK;
        }
        if (any(q, BIOMETRICS)) {
            return Command.BIOMETRICS;
        }
        if (any(q, HARD_LIMITS)) {
            return Command.HARD_LIMITS;
        }
        if (any(q, SETTINGS)) {
            return Command.SETTINGS;
        }
        if (any(q, DAILY_HARVEST)) {
            return Command.DAILY_HARVEST;
        }
        if (any(q, FEAST)) {
            return Command.FEAST;
        }
        if (any(q, DASHBOARD)) {
            return Command.DASHBOARD;
        }

        return Command.NONE;
    }

    /** True when any keyword in {@code keywords} starts a word in {@code q}. */
    private static boolean any(@NonNull String q, @NonNull String[] keywords) {
        for (String keyword : keywords) {
            // Compiled per utterance rather than cached: this runs once per recognised
            // command, so a handful of tiny patterns is not worth a static cache.
            if (Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b").matcher(q).find()) {
                return true;
            }
        }
        return false;
    }
}
