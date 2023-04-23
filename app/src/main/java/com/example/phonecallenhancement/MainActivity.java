package com.example.phonecallenhancement;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.picovoice.koala.Koala;
import ai.picovoice.koala.KoalaActivationException;
import ai.picovoice.koala.KoalaActivationLimitException;
import ai.picovoice.koala.KoalaActivationRefusedException;
import ai.picovoice.koala.KoalaActivationThrottledException;
import ai.picovoice.koala.KoalaException;
import ai.picovoice.koala.KoalaInvalidArgumentException;

public class MainActivity extends AppCompatActivity {

    // TODO (Walkie-Talkie protocol):
    //  1) Get recording audio file from mic
    //  2) send it over websocket
    //  3) receive back reply and decode the data of Stringbase64Data
    //  4) Got audio file, "enhance" it + transcript
    //  5) Repeat step 1

    // CONSTANTS
    public static int MY_PERMISSIONS_RECORD_AUDIO = 1;
    public static int MY_PERMISSIONS_INTERNET = 2;
    private static final String TAG = "Romero";

    // UI COMPONENTS
    private ToggleButton recordButton;
    private ActionBar actionBar;
    private AudioRecord audioRecord;
    private VisualizerView beforeProcessWave, afterProcessWave;
    private TextView recordedText;

    //---------FIELDS------------

    private static final String ACCESS_KEY = BuildConfig.PICOVOICE_API_KEY;

    // koala API that is used for speech enhancement https://picovoice.ai/docs/koala/
    // cheeatah API for real-time transcription https://picovoice.ai/platform/cheetah/
    // nb: can use google speech-to-text but above 2 is easier to use
    public Koala koala;
    private int bufferSize;
    private BlockingQueue<short[]> audioDataQueue;
    public short[] getAudioData() throws InterruptedException {
        return audioDataQueue.take();
    }
    private String referenceFilepath;
    private String enhancedFilepath;
    private MediaPlayer referenceMediaPlayer;
    private MediaPlayer enhancedMediaPlayer;
    private MicrophoneReader microphoneReader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recordButton = this.findViewById(R.id.recordButton);
        beforeProcessWave = this.findViewById(R.id.beforeWave);
        afterProcessWave = this.findViewById(R.id.afterWave);
        recordedText = this.findViewById(R.id.recordedText);

        actionBar = getSupportActionBar();
        actionBar.setTitle("Jancok");
        actionBar.setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.bg, null));
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP);

        initKoala();
        Log.d("Romero","koala rate: " + koala.getSampleRate()); //16000 hz

        microphoneReader = new MicrophoneReader();

        referenceFilepath = getApplicationContext().getFileStreamPath("reference.wav").getAbsolutePath();
        Log.d("Romero", "referenceFilepath: " + referenceFilepath);
        enhancedFilepath = getApplicationContext().getFileStreamPath("enhanced.wav").getAbsolutePath();
        Log.d("Romero", "enhancedFilepath: " + enhancedFilepath);
        referenceMediaPlayer = new MediaPlayer();
        enhancedMediaPlayer = new MediaPlayer();
        // Disable looping
        referenceMediaPlayer.setLooping(false);
        enhancedMediaPlayer.setLooping(false);
        referenceMediaPlayer.setVolume(0, 0);
        enhancedMediaPlayer.setVolume(1, 1);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        checkRecordAudioPermission();
        checkInternetPermission();
        setupVisualizerFxAndUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (referenceMediaPlayer != null) {
            referenceMediaPlayer.release();
        }
        if (enhancedMediaPlayer != null) {
            enhancedMediaPlayer.release();
        }
        koala.delete();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing() && audioRecord != null) {
            stopRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted, proceed with audio capture

                try {
                    microphoneReader.start();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                startRecording();
            } else {
                // Permission has been denied, show a message to the user or handle the error
                Toast.makeText(this, "Audio capture permission denied", Toast.LENGTH_SHORT).show();
                ToggleButton toggleButton = findViewById(R.id.recordButton);
                toggleButton.toggle();
            }
        }
    }

    //---------LISTENERS--------------

    public void onClickRecord(View view) {
        try {
            if (recordButton.isChecked()) {
                if (referenceMediaPlayer.isPlaying()) {
                    referenceMediaPlayer.stop();
                }
                if (enhancedMediaPlayer.isPlaying()) {
                    enhancedMediaPlayer.stop();
                }

                if (hasRecordPermission()) {
                    microphoneReader.start();
                } else {
                    checkRecordAudioPermission();
                }
            } else {
                microphoneReader.stop();

                resetMediaPlayer(referenceMediaPlayer, referenceFilepath);
                resetMediaPlayer(enhancedMediaPlayer, enhancedFilepath);

                // play on speaker
                // and disable looping playback
                referenceMediaPlayer.start();
                enhancedMediaPlayer.start();
            }
        } catch (InterruptedException | IOException e) {
            Toast.makeText(this, "Audio stop command interrupted\n", Toast.LENGTH_SHORT).show();
        }
    }

    public void onButtonClick(View view) {
        Button b = (Button) view;
        if (b.equals(findViewById(R.id.recordButton))) {
            Toast.makeText(this, "HI! " + b.getText(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "You clicked on " + b.getText(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onKoalaInitError(String error) {
        TextView errorMessage = findViewById(R.id.errorMessage);
        errorMessage.setText(error);
        errorMessage.setVisibility(View.VISIBLE);

        recordButton.setEnabled(false);
        recordButton.setError("error");
    }

    //--------HELPERS----------

    private void initKoala() {
        try {
            koala = new Koala.Builder().setAccessKey(ACCESS_KEY).build(getApplicationContext());
        } catch (KoalaInvalidArgumentException e) {
            onKoalaInitError(String.format("AccessKey '%s' is invalid", ACCESS_KEY));
        } catch (KoalaActivationException e) {
            onKoalaInitError("AccessKey activation error");
        } catch (KoalaActivationLimitException e) {
            onKoalaInitError("AccessKey reached its device limit");
        } catch (KoalaActivationRefusedException e) {
            onKoalaInitError("AccessKey refused");
        } catch (KoalaActivationThrottledException e) {
            onKoalaInitError("AccessKey has been throttled");
        } catch (KoalaException e) {
            onKoalaInitError("Failed to initialize Koala " + e.getMessage());
        }
    }

    private boolean hasRecordPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkRecordAudioPermission() {
        if (!hasRecordPermission()) {
            // Permission has not been granted yet, request it from the user
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_RECORD_AUDIO);
        } else {
            // Permission has already been granted, proceed with audio capture
            startRecording();
        }
    }

    private void checkInternetPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, MY_PERMISSIONS_INTERNET);
        }
    }

    @SuppressLint("MissingPermission")
    public void startRecording() {
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) ;

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);

        audioDataQueue = new LinkedBlockingQueue<short[]>();

        new Thread(new Runnable() {
            @Override
            public void run() {
                short[] buffer = new short[bufferSize];
                audioRecord.startRecording();
                while (!Thread.currentThread().isInterrupted()) {
                    int numBytes = audioRecord.read(buffer, 0, bufferSize);
                    short[] data = new short[numBytes];
                    System.arraycopy(buffer, 0, data, 0, numBytes);

                    try {
                        audioDataQueue.put(data);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                audioRecord.stop();
                audioRecord.release();
            }
        }).start();
    }

    public void stopRecording() {
        audioRecord.stop();
        audioRecord.release();
    }

    private void setupVisualizerFxAndUI() {
        // Create a VisualizerView (defined below), which will render the simplified audio
        // wave form to a Canvas.

        Timer timer = new Timer();
        long interval = 50; // 1/20 second in milliseconds

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // This code will be executed every 1/20 second
                try {
                    beforeProcessWave.updateVisualizer(getAudioData());
//                    AfterProcessWave.updateVisualizer(getAudioData());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, 0, interval);

    }

    private void resetMediaPlayer(MediaPlayer mediaPlayer, String audioFile) throws IOException {
        mediaPlayer.reset();
        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
        );
        mediaPlayer.setDataSource(audioFile);
        mediaPlayer.prepare();
    }

    // PRIVATE CLASS
    private class MicrophoneReader {
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean stop = new AtomicBoolean(false);
        private final AtomicBoolean stopped = new AtomicBoolean(false);
        final int wavHeaderLength = 44;
        private RandomAccessFile referenceFile;
        private RandomAccessFile enhancedFile;
        private int totalSamplesWritten;

        void start() throws IOException {

            if (started.get()) {
                return;
            }

            referenceFile = new RandomAccessFile(referenceFilepath, "rws");
            enhancedFile = new RandomAccessFile(enhancedFilepath, "rws");
            writeWavHeader(referenceFile, (short) 1, (short) 16, 16000, 0);
            writeWavHeader(enhancedFile, (short) 1, (short) 16, 16000, 0);

            started.set(true);

            Executors.newSingleThreadExecutor().submit((Callable<Void>) () -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                read();
                return null;
            });
        }

        void stop() throws InterruptedException, IOException {
            if (!started.get()) {
                return;
            }

            stop.set(true);

            while (!stopped.get()) {
                Thread.sleep(10);
            }

            writeWavHeader(referenceFile, (short) 1, (short) 16, 16000, totalSamplesWritten);
            writeWavHeader(enhancedFile, (short) 1, (short) 16, 16000, totalSamplesWritten);
            referenceFile.close();
            enhancedFile.close();

            started.set(false);
            stop.set(false);
            stopped.set(false);
        }

        @SuppressLint({"MissingPermission", "SetTextI18n", "DefaultLocale"})
        private void read() throws KoalaException {
            final int minBufferSize = AudioRecord.getMinBufferSize(
                    koala.getSampleRate(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            final int bufferSize = Math.max(koala.getSampleRate() / 2, minBufferSize);

            AudioRecord audioRecord = null;

            short[] frameBuffer = new short[koala.getFrameLength()];

            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        koala.getSampleRate(),
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);
                audioRecord.startRecording();

                final int koalaDelay = koala.getDelaySample();

                totalSamplesWritten = 0;
                int enhancedSamplesWritten = 0;
                while (!stop.get()) {
                    if (audioRecord.read(frameBuffer, 0, frameBuffer.length) == frameBuffer.length) {
                        final short[] frameBufferEnhanced = koala.process(frameBuffer);

                        writeFrame(referenceFile, frameBuffer);
                        totalSamplesWritten += frameBuffer.length;
                        if (totalSamplesWritten >= koalaDelay) {
                            writeFrame(enhancedFile, frameBufferEnhanced);
                            enhancedSamplesWritten += frameBufferEnhanced.length;
                        }
                    }

                    if ((totalSamplesWritten / koala.getFrameLength()) % 10 == 0) {
                        runOnUiThread(() -> {
                            double secondsRecorded = ((double) (totalSamplesWritten) / (double) (koala.getSampleRate()));
                            recordedText.setText(String.format("Recording: %.1fs", secondsRecorded));
                        });
                    }
                }

                audioRecord.stop();

                runOnUiThread(() -> {
                    double secondsRecorded = ((double) (totalSamplesWritten) / (double) (koala.getSampleRate()));
                    recordedText.setText(String.format("Recorded: %.1fs", secondsRecorded));
                });

                short[] emptyFrame = new short[koala.getFrameLength()];
                Arrays.fill(emptyFrame, (short) 0);
                while (enhancedSamplesWritten < totalSamplesWritten) {
                    final short[] frameBufferEnhanced = koala.process(emptyFrame);
                    writeFrame(enhancedFile, frameBufferEnhanced);
                    enhancedSamplesWritten += frameBufferEnhanced.length;
                }
            } catch (IllegalArgumentException | IllegalStateException | IOException e) {
                throw new KoalaException(e);
            } finally {
                if (audioRecord != null) {
                    audioRecord.release();
                }
                stopped.set(true);
            }
        }

        private void writeFrame(RandomAccessFile outputFile, short[] frame) throws IOException {
            ByteBuffer byteBuf = ByteBuffer.allocate(2 * frame.length);
            byteBuf.order(ByteOrder.LITTLE_ENDIAN);

            for (short s : frame) {
                byteBuf.putShort(s);
            }
            outputFile.write(byteBuf.array());
        }

        private void writeWavHeader(RandomAccessFile outputFile, short channelCount, short bitDepth, int sampleRate, int totalSampleCount) throws IOException {
            ByteBuffer byteBuf = ByteBuffer.allocate(wavHeaderLength);
            byteBuf.order(ByteOrder.LITTLE_ENDIAN);

            byteBuf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
            byteBuf.putInt((bitDepth / 8 * totalSampleCount) + 36);
            byteBuf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
            byteBuf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
            byteBuf.putInt(16);
            byteBuf.putShort((short) 1);
            byteBuf.putShort(channelCount);
            byteBuf.putInt(sampleRate);
            byteBuf.putInt(sampleRate * channelCount * bitDepth / 8);
            byteBuf.putShort((short) (channelCount * bitDepth / 8));
            byteBuf.putShort(bitDepth);
            byteBuf.put("data".getBytes(StandardCharsets.US_ASCII));
            byteBuf.putInt(bitDepth / 8 * totalSampleCount);

            outputFile.seek(0);
            outputFile.write(byteBuf.array());
        }
    }
}