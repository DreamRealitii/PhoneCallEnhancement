package com.example.phonecallenhancement;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeRequest;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SpeechToText {

    private SpeechClient speechClient;

    public SpeechToText() throws IOException {
        InputStream credentialsStream = getCredentialsStream();
        GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
        FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
        speechClient = SpeechClient.create(SpeechSettings.newBuilder().setCredentialsProvider(credentialsProvider).build());
    }

    public String transcribe(byte[] audioData, String languageCode) throws IOException {
        ByteString audioBytes = ByteString.copyFrom(audioData);
        RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();
        RecognitionConfig config =
                RecognitionConfig.newBuilder()
                        .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                        .setSampleRateHertz(16000)
                        .setLanguageCode(languageCode)
                        .build();
        RecognizeRequest request = RecognizeRequest.newBuilder().setAudio(audio).setConfig(config).build();
        RecognizeResponse response = speechClient.recognize(request);
        List<String> results = new ArrayList<>();
        for (com.google.cloud.speech.v1.SpeechRecognitionResult result : response.getResultsList()) {
            for (com.google.cloud.speech.v1.SpeechRecognitionAlternative alternative : result.getAlternativesList()) {
                results.add(alternative.getTranscript());
            }
        }
        return String.join("\n", results);
    }

    private InputStream getCredentialsStream() {
        // TODO: Replace with your own credentials file name
        String credentialsFileName = "google_credentials.json";
        return getClass().getClassLoader().getResourceAsStream(credentialsFileName);
    }
}
