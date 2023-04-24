package com.example.phonecallenhancement.unused;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.phonecallenhancement.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InternalSpeechToText extends AppCompatActivity implements RecognitionListener {
    private SpeechRecognizer speechRecognizer;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.speech_text);

        textView = findViewById(R.id.textView);

        // Check if there are any speech recognition services installed
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> services = packageManager.queryIntentServices(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);

        if (services.size() == 0) {
            Log.d("Romero", "Speech recognition not supported on this device");
        } else {
            // Speech recognition supported, continue with initialization
            Log.d("Romero", "Speech recognition supported");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            } else {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }

            speechRecognizer.setRecognitionListener(this);
        }

        String serviceComponent = Settings.Secure.getString(this.getContentResolver(), "voice_recognition_service");
        ComponentName cn = ComponentName.unflattenFromString(serviceComponent);
        System.out.println(cn.toShortString());
//        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this, cn);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.d("Romero", "ondevice " + SpeechRecognizer.isOnDeviceRecognitionAvailable(this));
        } else {
            Log.d("Romero","recog " + SpeechRecognizer.isRecognitionAvailable(this));
        }
    }

    public void startSpeechRecognition(View view) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onReadyForSpeech(Bundle bundle) {
        Log.d("Speech", "Ready for speech");
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d("Speech", "Beginning of speech");
    }

    @Override
    public void onRmsChanged(float v) {
        Log.d("Speech", "RMS changed");
    }

    @Override
    public void onBufferReceived(byte[] bytes) {
        Log.d("Speech", "Buffer received");
    }

    @Override
    public void onEndOfSpeech() {
        Log.d("Speech", "End of speech");
    }

    @Override
    public void onError(int i) {
        Log.d("Speech", "Error: " + i);
    }

    @Override
    public void onResults(Bundle bundle) {
        ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (results != null && !results.isEmpty()) {
            String text = results.get(0);
            textView.setText(text);
        }
    }

    @Override
    public void onPartialResults(Bundle bundle) {
        Log.d("Speech", "Partial results");
    }

    @Override
    public void onEvent(int i, Bundle bundle) {
        Log.d("Speech", "Event: " + i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        speechRecognizer.destroy();
    }
}