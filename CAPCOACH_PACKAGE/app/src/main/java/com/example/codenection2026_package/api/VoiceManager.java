package com.example.codenection2026_package.api;

import android.content.Context;
import android.content.Intent;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

public class VoiceManager {

    private final SpeechRecognizer speechRecognizer;
    private final Intent speechIntent;

    public VoiceManager(Context context, RecognitionListener listener) {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(listener);

        // Configure for on-device, free-form speech
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        // Forces local processing to maintain offline-first privacy
        speechIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
    }

    public void startListening() {
        speechRecognizer.startListening(speechIntent);
    }

    public void stopListening() {
        speechRecognizer.stopListening();
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}