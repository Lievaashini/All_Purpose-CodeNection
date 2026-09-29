package com.example.codenection2026_package.api;

public class VoiceInputFormatter {

    /**
     * Sanitizes raw SpeechRecognizer output for the Add Task title field.
     */
    public static String formatTaskTitle(String rawSpeech) {
        if (rawSpeech == null || rawSpeech.trim().isEmpty()) {
            return "";
        }

        // Strip trailing punctuation often added by speech-to-text
        String cleaned = rawSpeech.replaceAll("[.,!?]$", "").trim();

        if (cleaned.isEmpty()) {
            return "";
        }

        // Capitalize the first letter for UI consistency
        cleaned = cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);

        // Hard cap at 40 characters to prevent UI overflow in the modal
        if (cleaned.length() > 40) {
            cleaned = cleaned.substring(0, 40).trim();
        }

        return cleaned;
    }
}
