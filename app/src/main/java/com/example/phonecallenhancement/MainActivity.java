//package com.example.phonecallenhancement;
//
//import static android.util.Base64.URL_SAFE;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.ActionBar;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.res.ResourcesCompat;
//
//import android.Manifest;
//import android.annotation.SuppressLint;
//import android.content.pm.PackageManager;
//import android.media.AudioAttributes;
//import android.media.AudioFormat;
//import android.media.AudioManager;
//import android.media.AudioRecord;
//import android.media.MediaPlayer;
//import android.media.MediaRecorder;
//import android.os.Bundle;
//import android.os.Process;
//import android.util.Base64;
//import android.util.Log;
//import android.view.View;
//import android.widget.TextView;
//import android.widget.Toast;
//import android.widget.ToggleButton;
//
//import java.io.IOException;
//import java.io.RandomAccessFile;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.Arrays;
//import java.util.Timer;
//import java.util.TimerTask;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.Callable;
//import java.util.concurrent.Executors;
//import java.util.concurrent.LinkedBlockingQueue;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import ai.picovoice.koala.Koala;
//import ai.picovoice.koala.KoalaActivationException;
//import ai.picovoice.koala.KoalaActivationLimitException;
//import ai.picovoice.koala.KoalaActivationRefusedException;
//import ai.picovoice.koala.KoalaActivationThrottledException;
//import ai.picovoice.koala.KoalaException;
//import ai.picovoice.koala.KoalaInvalidArgumentException;
//import ai.picovoice.leopard.Leopard;
//import ai.picovoice.leopard.LeopardActivationException;
//import ai.picovoice.leopard.LeopardActivationLimitException;
//import ai.picovoice.leopard.LeopardActivationRefusedException;
//import ai.picovoice.leopard.LeopardActivationThrottledException;
//import ai.picovoice.leopard.LeopardException;
//import ai.picovoice.leopard.LeopardInvalidArgumentException;
//import ai.picovoice.leopard.LeopardTranscript;
//import ai.picovoice.cheetah.*;
//
//public class MainActivity extends AppCompatActivity {
//
//    // TODO (Walkie-Talkie protocol):
//    //  1) Get recording audio file from mic
//    //  2) send it over websocket
//    //  3) receive back reply and decode the data of Stringbase64Data
//    //  4) Got audio file, "enhance" it + transcript
//    //  5) Repeat step 1
//
//    // CONSTANTS
//    public static int MY_PERMISSIONS_RECORD_AUDIO = 1;
//    public static int MY_PERMISSIONS_INTERNET = 2;
//    // Filter usage for logcat:
//    // package:mine & (level:error | level:debug & tag:Debugging)
//    private static final String TAG = "Debugging";
//
//    // UI COMPONENTS
//    private ToggleButton recordButton;
//    private ActionBar actionBar;
//    private AudioRecord audioRecord;
//    private VisualizerView beforeProcessWave, afterProcessWave;
//    private TextView recordedText;
//    private TextView transcriptText;
//
//    //---------FIELDS------------
//
//    private static final String ACCESS_KEY = BuildConfig.PICOVOICE_API_KEY;
//
//    // koala API that is used for speech enhancement https://picovoice.ai/docs/koala/
//    // cheeatah API for real-time transcription https://picovoice.ai/platform/cheetah/
//    // leopard API for asynchronous transcription https://picovoice.ai/platform/cat/
//    // nb: can use google speech-to-text but above 2 is easier to use
//    // nb2: Spent 15+ hrs trying to make google speech-to-text work but it's impossible to do...
//
//    public Koala koala;
//    private static final String LEOPARD_MODEL_FILE = "leopard_params.pv";
//    private static final String CHEETAH_MODEL_FILE = "cheetah_params.pv";
//    private Leopard leopard;
//    private Cheetah cheetah;
//    private int bufferSize;
//    private BlockingQueue<short[]> audioDataQueue;
//    public short[] getAudioData() throws InterruptedException {
//        return audioDataQueue.take();
//    }
//    private String referenceFilepath;
//    private String enhancedFilepath;
//    private MediaPlayer referenceMediaPlayer;
//    private MediaPlayer enhancedMediaPlayer;
//    private MicrophoneReader microphoneReader = new MicrophoneReader();
//    private final AtomicBoolean stop = new AtomicBoolean(false);
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        // UI Initialization
//        recordButton = this.findViewById(R.id.recordButton);
//        beforeProcessWave = this.findViewById(R.id.beforeWave);
//        afterProcessWave = this.findViewById(R.id.afterWave);
//        recordedText = this.findViewById(R.id.recordedText);
//        transcriptText = this.findViewById(R.id.transcriptContentTv);
//
//        actionBar = getSupportActionBar();
//        actionBar.setTitle("Speech Enhancement");
//        actionBar.setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.bg, null));
//        actionBar.setHomeButtonEnabled(true);
//        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP);
//
//        // Initialize voice processing
//        initKoala();
//        Log.d(TAG,"koala rate: " + koala.getSampleRate()); //16000 hz
//        // initLeopard();
//        // Log.d(TAG, "Leopard version: " + leopard.getVersion());
//        initCheetah();
//        Log.d(TAG, "Cheetah version: " + cheetah.getVersion());
//
//        //microphoneReader = new MicrophoneReader();
//
//        // Get the reference + enhanced audio locations and settings
//        referenceFilepath = getApplicationContext().getFileStreamPath("reference.wav").getAbsolutePath();
//        Log.d(TAG, "referenceFilepath: " + referenceFilepath);
//        enhancedFilepath = getApplicationContext().getFileStreamPath("enhanced.wav").getAbsolutePath();
//        Log.d(TAG, "enhancedFilepath: " + enhancedFilepath);
//        referenceMediaPlayer = new MediaPlayer();
//        enhancedMediaPlayer = new MediaPlayer();
//        // Disable looping
//        referenceMediaPlayer.setLooping(false);
//        enhancedMediaPlayer.setLooping(false);
//        referenceMediaPlayer.setVolume(0, 0);
//        enhancedMediaPlayer.setVolume(1, 1);
//
//        setVolumeControlStream(AudioManager.STREAM_MUSIC);
//        checkRecordAudioPermission();
//        checkInternetPermission();
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (isFinishing() && audioRecord != null) {
//            stopRecording();
//        }
//        if (referenceMediaPlayer != null) {
//            referenceMediaPlayer.release();
//        }
//        if (enhancedMediaPlayer != null) {
//            enhancedMediaPlayer.release();
//        }
//        koala.delete();
//        //leopard.delete();
//        cheetah.delete();
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (isFinishing() && audioRecord != null) {
//            stopRecording();
//        }
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == MY_PERMISSIONS_RECORD_AUDIO) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Permission has been granted, proceed with audio capture
//
//                try {
//                    microphoneReader.start();
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//
//                audioRecord.startRecording();
//            } else {
//                // Permission has been denied, show a message to the user or handle the error
//                Toast.makeText(this, "Audio capture permission denied", Toast.LENGTH_SHORT).show();
//                ToggleButton toggleButton = findViewById(R.id.recordButton);
//                toggleButton.toggle();
//            }
//        }
//    }
//
//    //---------LISTENERS--------------
//
//    public void onClickRecord(View view) {
//        try {
//            if (recordButton.isChecked()) {
//                if (referenceMediaPlayer.isPlaying()) {
//                    referenceMediaPlayer.stop();
//                }
//                if (enhancedMediaPlayer.isPlaying()) {
//                    enhancedMediaPlayer.stop();
//                }
//
//                if (hasRecordPermission()) {
//                    stop.set(false);
//                    startRecording();
//                    setupVisualizerFxAndUI();
//
//                    microphoneReader.start();
//                } else {
//                    checkRecordAudioPermission();
//                }
//            } else {
//                stop.set(true);
//                microphoneReader.stop();
//
//                resetMediaPlayer(referenceMediaPlayer, referenceFilepath);
//                resetMediaPlayer(enhancedMediaPlayer, enhancedFilepath);
//
//                // LeopardTranscript transcript = leopard.processFile(referenceFilepath);
//                // transcriptText.setText(transcript.getTranscriptString());
//                // Log.d(TAG, "transcript: " + transcript.getTranscriptString());
//
//                // play on speaker
//                // and disable looping playback
//                referenceMediaPlayer.start();
//                enhancedMediaPlayer.start();
//            }
//        } catch (InterruptedException | IOException e) {
//            e.printStackTrace();
//            // Toast.makeText(this, "Audio stop command interrupted\n", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private void onInitError(String error) {
//        TextView errorMessage = findViewById(R.id.errorMessage);
//        errorMessage.setText(error);
//        errorMessage.setVisibility(View.VISIBLE);
//
//        recordButton.setEnabled(false);
//        recordButton.setError("error");
//    }
//
//    private void updateTranscriptView(String transcript) {
//        runOnUiThread(() -> {
//            if (transcript.length() != 0) {
//                transcriptText.append(transcript);
//
//                final int scrollAmount = transcriptText.getLayout().getLineTop(transcriptText.getLineCount()) -
//                        transcriptText.getHeight() +
//                        transcriptText.getLineHeight();
//
//                if (scrollAmount > 0) {
//                    transcriptText.scrollTo(0, scrollAmount);
//                }
//            }
//        });
//    }
//
//    //--------HELPERS----------
//
//    private String wavToBase64(String path) {
//
//        byte[] bytes = new byte[0];
//
//        try {
//            bytes = Files.readAllBytes(Paths.get(path));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        String encoded = Base64.encodeToString(bytes, URL_SAFE);
//        //String encoded = new String(Base64.encodeBase64URLSafe(bytes));
////        Log.d(TAG, "wavToBase64: " + encoded);
//
//        return encoded;
//    }
//
//    private void initKoala() {
//        try {
//            koala = new Koala.Builder()
//                    .setAccessKey(ACCESS_KEY)
//                    .build(getApplicationContext());
//        } catch (KoalaInvalidArgumentException e) {
//            onInitError(String.format("AccessKey '%s' is invalid", ACCESS_KEY));
//        } catch (KoalaActivationException e) {
//            onInitError("AccessKey activation error");
//        } catch (KoalaActivationLimitException e) {
//            onInitError("AccessKey reached its device limit");
//        } catch (KoalaActivationRefusedException e) {
//            onInitError("AccessKey refused");
//        } catch (KoalaActivationThrottledException e) {
//            onInitError("AccessKey has been throttled");
//        } catch (KoalaException e) {
//            onInitError("Failed to initialize Koala " + e.getMessage());
//        }
//    }
//
//    private void initLeopard() {
//        try {
//            leopard = new Leopard.Builder()
//                    .setAccessKey(ACCESS_KEY)
//                    .setModelPath(LEOPARD_MODEL_FILE)
//                    .setEnableAutomaticPunctuation(true)
//                    .build(getApplicationContext());
//        } catch (LeopardInvalidArgumentException e) {
//            onInitError(String.format("%s\nEnsure your AccessKey '%s' is valid", e.getMessage(), ACCESS_KEY));
//        } catch (LeopardActivationException e) {
//            onInitError("AccessKey activation error");
//        } catch (LeopardActivationLimitException e) {
//            onInitError("AccessKey reached its device limit");
//        } catch (LeopardActivationRefusedException e) {
//            onInitError("AccessKey refused");
//        } catch (LeopardActivationThrottledException e) {
//            onInitError("AccessKey has been throttled");
//        } catch (LeopardException e) {
//            onInitError("Failed to initialize Leopard " + e.getMessage());
//        }
//    }
//
//    private void initCheetah() {
//        try {
//            cheetah = new Cheetah.Builder()
//                    .setAccessKey(ACCESS_KEY)
//                    .setModelPath(CHEETAH_MODEL_FILE)
//                    .setEnableAutomaticPunctuation(true)
//                    .build(getApplicationContext());
//        } catch (CheetahInvalidArgumentException e) {
//            onInitError(String.format("%s\nEnsure your AccessKey '%s' is valid", e.getMessage(), ACCESS_KEY));
//        } catch (CheetahActivationException e) {
//            onInitError("AccessKey activation error");
//        } catch (CheetahActivationLimitException e) {
//            onInitError("AccessKey reached its device limit");
//        } catch (CheetahActivationRefusedException e) {
//            onInitError("AccessKey refused");
//        } catch (CheetahActivationThrottledException e) {
//            onInitError("AccessKey has been throttled");
//        } catch (CheetahException e) {
//            onInitError("Failed to initialize Cheetah " + e.getMessage());
//        }
//    }
//
//    private boolean hasRecordPermission() {
//        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
//    }
//
//    private void checkRecordAudioPermission() {
//        if (!hasRecordPermission()) {
//            // Permission has not been granted yet, request it from the user
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_RECORD_AUDIO);
//        }
//    }
//
//    private void checkInternetPermission() {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, MY_PERMISSIONS_INTERNET);
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    public void startRecording() {
//        int sampleRate = 44100;
//        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
//        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
//        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) ;
//
//        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
//
//        audioDataQueue = new LinkedBlockingQueue<short[]>();
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                short[] buffer = new short[bufferSize];
//                audioRecord.startRecording();
//                while (!stop.get()) {
//                    int numBytes = audioRecord.read(buffer, 0, bufferSize);
//                    short[] data = new short[numBytes];
//                    System.arraycopy(buffer, 0, data, 0, numBytes);
//
//                    try {
//                        audioDataQueue.put(data);
//                    } catch (InterruptedException e) {
//                        Thread.currentThread().interrupt();
//                    }
//                }
//                audioRecord.stop();
//                audioRecord.release();
//            }
//        }).start();
//    }
//
//    public void stopRecording() {
//        if (!stop.get()) {
//            audioRecord.stop();
//            audioRecord.release();
//        }
//    }
//
//    private void setupVisualizerFxAndUI() {
//        // Create a VisualizerView (defined below), which will render the simplified audio
//        // wave form to a Canvas.
//
//        Timer timer = new Timer();
//        long interval = 50; // 1/20 second in milliseconds
//
//        timer.scheduleAtFixedRate(new TimerTask() {
//            @Override
//            public void run() {
//                // This code will be executed every 1/20 second
//                try {
//                    if (audioDataQueue == null) {
//                        beforeProcessWave.updateVisualizer(null);
////                    AfterProcessWave.updateVisualizer(null);
//                    } else {
//                        beforeProcessWave.updateVisualizer(getAudioData());
////                    AfterProcessWave.updateVisualizer(getAudioData());
//                    }
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }, 0, interval);
//
//    }
//
//    private void resetMediaPlayer(MediaPlayer mediaPlayer, String audioFile) throws IOException {
//        mediaPlayer.reset();
//        mediaPlayer.setAudioAttributes(
//                new AudioAttributes.Builder()
//                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                        .setUsage(AudioAttributes.USAGE_MEDIA)
//                        .build()
//        );
//        mediaPlayer.setDataSource(audioFile);
//        mediaPlayer.prepare();
//    }
//
//    // PRIVATE CLASS
//    private class MicrophoneReader {
//        private final AtomicBoolean started = new AtomicBoolean(false);
//        private final AtomicBoolean stop = new AtomicBoolean(false);
//        private final AtomicBoolean stopped = new AtomicBoolean(false);
//        final int wavHeaderLength = 44;
//        private RandomAccessFile referenceFile;
//        private RandomAccessFile enhancedFile;
//        private int totalSamplesWritten;
//
//        void start() throws IOException {
//
//            if (started.get()) {
//                return;
//            }
//
//            referenceFile = new RandomAccessFile(referenceFilepath, "rws");
//            enhancedFile = new RandomAccessFile(enhancedFilepath, "rws");
//            writeWavHeader(referenceFile, (short) 1, (short) 16, 16000, 0);
//            writeWavHeader(enhancedFile, (short) 1, (short) 16, 16000, 0);
//
//            started.set(true);
//
//            Executors.newSingleThreadExecutor().submit((Callable<Void>) () -> {
//                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
//                read();
//                return null;
//            });
//        }
//
//        void stop() throws InterruptedException, IOException {
//            if (!started.get()) {
//                return;
//            }
//
//            stop.set(true);
//
//            while (!stopped.get()) {
//                Thread.sleep(10);
//            }
//
//            writeWavHeader(referenceFile, (short) 1, (short) 16, 16000, totalSamplesWritten);
//            writeWavHeader(enhancedFile, (short) 1, (short) 16, 16000, totalSamplesWritten);
//            referenceFile.close();
//            enhancedFile.close();
//
//            started.set(false);
//            stop.set(false);
//            stopped.set(false);
//        }
//
//        @SuppressLint({"MissingPermission", "SetTextI18n", "DefaultLocale"})
//        private void read() throws KoalaException, CheetahException {
//            final int minBufferSize = AudioRecord.getMinBufferSize(
//                    cheetah.getSampleRate(),
//                    AudioFormat.CHANNEL_IN_MONO,
//                    AudioFormat.ENCODING_PCM_16BIT);
//            final int bufferSize = Math.max(cheetah.getSampleRate() / 2, minBufferSize);
//
//            AudioRecord audioRecord = null;
//
//            short[] frameBuffer = new short[cheetah.getFrameLength()];
//
//            try {
//                audioRecord = new AudioRecord(
//                        MediaRecorder.AudioSource.MIC,
//                        cheetah.getSampleRate(),
//                        AudioFormat.CHANNEL_IN_MONO,
//                        AudioFormat.ENCODING_PCM_16BIT,
//                        bufferSize);
//                audioRecord.startRecording();
//
//                //final int koalaDelay = koala.getDelaySample();
//
//                totalSamplesWritten = 0;
//                int enhancedSamplesWritten = 0;
//                while (!stop.get()) {
//                    if (audioRecord.read(frameBuffer, 0, frameBuffer.length) == frameBuffer.length) {
//                        // Koala enhancements
//                        final short[] frameBufferEnhanced = koala.process(frameBuffer);
//                        writeFrame(referenceFile, frameBuffer);
//                        totalSamplesWritten += frameBuffer.length;
//                        //if (totalSamplesWritten >= koalaDelay) {
//                        //    writeFrame(enhancedFile, frameBufferEnhanced);
//                        //    enhancedSamplesWritten += frameBufferEnhanced.length;
//                        //}
//
//                        // Cheetah transcript
//                        CheetahTranscript transcript = cheetah.process(frameBuffer);
//                        updateTranscriptView(transcript.getTranscript() + " ");
//
//                        if (transcript.getIsEndpoint()) {
//                            transcript = cheetah.flush();
//                            updateTranscriptView(transcript.getTranscript() + " ");
//                        }
//                    }
//
//                    if ((totalSamplesWritten / cheetah.getFrameLength()) % 10 == 0) {
//                        runOnUiThread(() -> {
//                            double secondsRecorded = ((double) (totalSamplesWritten) / (double) (cheetah.getSampleRate()));
//                            recordedText.setText(String.format("Recording: %.1fs", secondsRecorded));
//                        });
//                    }
//                }
//
//                CheetahTranscript transcript = cheetah.flush();
//                updateTranscriptView(transcript.getTranscript());
//
//                audioRecord.stop();
//
//                runOnUiThread(() -> {
//                    double secondsRecorded = ((double) (totalSamplesWritten) / (double) (koala.getSampleRate()));
//                    recordedText.setText(String.format("Recorded: %.1fs", secondsRecorded));
//                });
//
////                short[] emptyFrame = new short[koala.getFrameLength()];
////                Arrays.fill(emptyFrame, (short) 0);
////                while (enhancedSamplesWritten < totalSamplesWritten) {
////                    final short[] frameBufferEnhanced = koala.process(emptyFrame);
////                    writeFrame(enhancedFile, frameBufferEnhanced);
////                    enhancedSamplesWritten += frameBufferEnhanced.length;
////                }
//            } catch (IllegalArgumentException | IllegalStateException | IOException e) {
//                throw new KoalaException(e);
//            } finally {
//                if (audioRecord != null) {
//                    audioRecord.release();
//                }
//                stopped.set(true);
//            }
//        }
//
//        private void writeFrame(RandomAccessFile outputFile, short[] frame) throws IOException {
//            ByteBuffer byteBuf = ByteBuffer.allocate(2 * frame.length);
//            byteBuf.order(ByteOrder.LITTLE_ENDIAN);
//
//            for (short s : frame) {
//                byteBuf.putShort(s);
//            }
//            outputFile.write(byteBuf.array());
//        }
//
//        private void writeWavHeader(RandomAccessFile outputFile, short channelCount, short bitDepth, int sampleRate, int totalSampleCount) throws IOException {
//            ByteBuffer byteBuf = ByteBuffer.allocate(wavHeaderLength);
//            byteBuf.order(ByteOrder.LITTLE_ENDIAN);
//
//            byteBuf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
//            byteBuf.putInt((bitDepth / 8 * totalSampleCount) + 36);
//            byteBuf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
//            byteBuf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
//            byteBuf.putInt(16);
//            byteBuf.putShort((short) 1);
//            byteBuf.putShort(channelCount);
//            byteBuf.putInt(sampleRate);
//            byteBuf.putInt(sampleRate * channelCount * bitDepth / 8);
//            byteBuf.putShort((short) (channelCount * bitDepth / 8));
//            byteBuf.putShort(bitDepth);
//            byteBuf.put("data".getBytes(StandardCharsets.US_ASCII));
//            byteBuf.putInt(bitDepth / 8 * totalSampleCount);
//
//            outputFile.seek(0);
//            outputFile.write(byteBuf.array());
//        }
//    }
//}

/*
    Copyright 2022-2023 Picovoice Inc.

    You may not use this file except in compliance with the license. A copy of the license is
    located in the "LICENSE" file accompanying this source.

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
    express or implied. See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.example.phonecallenhancement;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.picovoice.cheetah.Cheetah;
import ai.picovoice.cheetah.CheetahActivationException;
import ai.picovoice.cheetah.CheetahActivationLimitException;
import ai.picovoice.cheetah.CheetahActivationRefusedException;
import ai.picovoice.cheetah.CheetahActivationThrottledException;
import ai.picovoice.cheetah.CheetahException;
import ai.picovoice.cheetah.CheetahInvalidArgumentException;
import ai.picovoice.cheetah.CheetahTranscript;

public class MainActivity extends AppCompatActivity {
    private static final String ACCESS_KEY = BuildConfig.PICOVOICE_API_KEY;
    private static final String MODEL_FILE = "cheetah_params.pv";

    private final MicrophoneReader microphoneReader = new MicrophoneReader();
    public Cheetah cheetah;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView transcriptTextView = findViewById(R.id.transcriptContentTv);
        transcriptTextView.setMovementMethod(new ScrollingMovementMethod());
//        // UI Initialization
//        recordButton = this.findViewById(R.id.recordButton);
//        beforeProcessWave = this.findViewById(R.id.beforeWave);
//        afterProcessWave = this.findViewById(R.id.afterWave);
//        recordedText = this.findViewById(R.id.recordedText);
//        transcriptText = this.findViewById(R.id.transcriptContentTv);
        try {
            cheetah = new Cheetah.Builder()
                    .setAccessKey(ACCESS_KEY)
                    .setModelPath(MODEL_FILE)
                    .setEndpointDuration(1f)
                    .setEnableAutomaticPunctuation(true)
                    .build(getApplicationContext());
        } catch (CheetahInvalidArgumentException e) {
            displayError(String.format("%s\nEnsure your AccessKey '%s' is valid", e.getMessage(), ACCESS_KEY));
        } catch (CheetahActivationException e) {
            displayError("AccessKey activation error");
        } catch (CheetahActivationLimitException e) {
            displayError("AccessKey reached its device limit");
        } catch (CheetahActivationRefusedException e) {
            displayError("AccessKey refused");
        } catch (CheetahActivationThrottledException e) {
            displayError("AccessKey has been throttled");
        } catch (CheetahException e) {
            displayError("Failed to initialize Cheetah " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cheetah.delete();
    }

    private void displayError(String message) {
        TextView errorText = findViewById(R.id.errorMessage);
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);

        ToggleButton recordButton = findViewById(R.id.recordButton);
        recordButton.setEnabled(false);
    }

    private boolean hasRecordPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 0);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED) {
            ToggleButton toggleButton = findViewById(R.id.recordButton);
            toggleButton.toggle();
        } else {
            TextView recordingTextView = findViewById(R.id.recordedText);
            recordingTextView.setText("Recording...");
            microphoneReader.start();
        }
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    public void onClickRecord(View view) {
        ToggleButton recordButton = findViewById(R.id.recordButton);
        TextView recordingTextView = findViewById(R.id.recordedText);
        if (cheetah == null) {
            displayError("Cheetah is not initialized");
            recordButton.setChecked(false);
            return;
        }

        try {
            if (recordButton.isChecked()) {
                if (hasRecordPermission()) {
                    recordingTextView.setText("Recording...");
                    microphoneReader.start();
                } else {
                    requestRecordPermission();
                }
            } else {
                recordingTextView.setText("");
                microphoneReader.stop();
            }
        } catch (InterruptedException e) {
            displayError("Audio stop command interrupted\n" + e);
        }
    }

    private void updateTranscriptView(String transcript) {
        runOnUiThread(() -> {
            if (transcript.length() != 0) {
                TextView transcriptTextView = findViewById(R.id.transcriptContentTv);
                transcriptTextView.append(transcript);
                final int scrollAmount = transcriptTextView.getLayout().getLineTop(transcriptTextView.getLineCount()) -
                        transcriptTextView.getHeight() +
                        transcriptTextView.getLineHeight();

                if (scrollAmount > 0) {
                    transcriptTextView.scrollTo(0, scrollAmount);
                }
            }
        });
    }

    private class MicrophoneReader {
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean stop = new AtomicBoolean(false);
        private final AtomicBoolean stopped = new AtomicBoolean(false);

        void start() {
            if (started.get()) {
                return;
            }

            started.set(true);

            Executors.newSingleThreadExecutor().submit((Callable<Void>) () -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                read();
                return null;
            });
        }

        void stop() throws InterruptedException {
            if (!started.get()) {
                return;
            }

            stop.set(true);

            synchronized (stopped) {
                while (!stopped.get()) {
                    stopped.wait(500);
                }
            }

            started.set(false);
            stop.set(false);
            stopped.set(false);
        }

        private void read() throws CheetahException {
            final int minBufferSize = AudioRecord.getMinBufferSize(
                    cheetah.getSampleRate(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            final int bufferSize = Math.max(cheetah.getSampleRate() / 2, minBufferSize);

            AudioRecord audioRecord = null;

            short[] buffer = new short[cheetah.getFrameLength()];

            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        cheetah.getSampleRate(),
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);
                audioRecord.startRecording();


                while (!stop.get()) {
                    if (audioRecord.read(buffer, 0, buffer.length) == buffer.length) {
                        CheetahTranscript transcriptObj = cheetah.process(buffer);
                        updateTranscriptView(transcriptObj.getTranscript());
                        if(!transcriptObj.getTranscript().equals("")) {
                            Log.i("Che", "TS: " + transcriptObj.getTranscript());
                        }
                        if (transcriptObj.getIsEndpoint()) {
                            transcriptObj = cheetah.flush();
                            updateTranscriptView(transcriptObj.getTranscript() + " ");
                        }
                    }
                }

                final CheetahTranscript transcriptObj = cheetah.flush();
                updateTranscriptView(transcriptObj.getTranscript());

                audioRecord.stop();
            } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
                throw new CheetahException(e);
            } finally {
                if (audioRecord != null) {
                    audioRecord.release();
                }

                stopped.set(true);
                stopped.notifyAll();
            }
        }
    }
}