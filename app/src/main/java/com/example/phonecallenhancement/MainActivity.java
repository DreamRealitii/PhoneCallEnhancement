package com.example.phonecallenhancement;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.phonecallenhancement.unused.MicrophoneReader;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import Sound.VolumeControl;
import ai.picovoice.koala.Koala;
import ai.picovoice.koala.KoalaActivationException;
import ai.picovoice.koala.KoalaActivationLimitException;
import ai.picovoice.koala.KoalaActivationRefusedException;
import ai.picovoice.koala.KoalaActivationThrottledException;
import ai.picovoice.koala.KoalaException;
import ai.picovoice.koala.KoalaInvalidArgumentException;
import ai.picovoice.cheetah.*;


public class MainActivity extends AppCompatActivity {

    // CONSTANTS
    public static int MY_PERMISSIONS_RECORD_AUDIO = 1;
    public static int MY_PERMISSIONS_INTERNET = 2;
    public static int MY_PERMISSIONS_READ_MEDIA_VIDEO = 3;


    // Filter usage for logcat:
    // package:mine & (level:error | level:debug & tag:Debugging)
    private static final String TAG = "Debugging";

    // UI COMPONENTS
    private ToggleButton recordButton;
    private ActionBar actionBar;
    private AudioRecord audioRecord;
    private VisualizerView beforeProcessWave, afterProcessWave;
    private TextView recordedText;
    private TextView transcriptText;

    private TextView errorMessage;

    private WebSocketClient webSocket;

    //---------FIELDS------------

    private static final String ACCESS_KEY = BuildConfig.PICOVOICE_API_KEY;

    // koala API that is used for speech enhancement https://picovoice.ai/docs/koala/
    // cheeatah API for real-time transcription https://picovoice.ai/platform/cheetah/
    // leopard API for asynchronous transcription https://picovoice.ai/platform/cat/
    // nb: can use google speech-to-text but above 2 is easier to use
    // nb2: Spent 15+ hrs trying to make google speech-to-text work but it's impossible to do...

    public Koala koala;
    private static final String CHEETAH_MODEL_FILE = "cheetah_params.pv";
    private Cheetah cheetah;
    private int bufferSize;
    private BlockingQueue<short[]> audioDataQueue = new LinkedBlockingQueue<short[]>();
    public short[] getAudioData() throws InterruptedException {
        //Log.d("MAIN", "CALLED");
        return audioDataQueue.take();
    }
    private String referenceFilepath;
    private String enhancedFilepath;
    private MicrophoneReader microphoneReader;
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private File cache;
    private boolean isFirst = true;
    private final StringBuilder stringBuilder = new StringBuilder();
    private final LinkedList<String> usernames = new LinkedList<>();
    private final AtomicBoolean isGenerateYourName = new AtomicBoolean(true);
    private final AtomicBoolean isGenerateOtherName = new AtomicBoolean(true);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cache = getCacheDir();
        recordButton = this.findViewById(R.id.recordButton);
        beforeProcessWave = this.findViewById(R.id.beforeWave);
        afterProcessWave = this.findViewById(R.id.afterWave);
        recordedText = this.findViewById(R.id.recordedText);
        transcriptText = this.findViewById(R.id.transcriptContentTv);
        transcriptText.setMovementMethod(new ScrollingMovementMethod());
        errorMessage = findViewById(R.id.errorMessage);

        actionBar = getSupportActionBar();
        actionBar.setTitle("Speech Enhancement");

        initKoala();
        Log.d(TAG,"koala rate: " + koala.getSampleRate()); //16000 hz
        Log.d(TAG,"Koala buffer: " + koala.getFrameLength());  // 256
        initCheetah();
        Log.d(TAG, "Cheetah version: " + cheetah.getVersion());
        Log.d(TAG,"Cheetah rate: " + cheetah.getSampleRate()); //16000 hz
        Log.d(TAG,"Cheetah buffer: " + cheetah.getFrameLength());  // 512



        microphoneReader = new MicrophoneReader();

        referenceFilepath = getApplicationContext().getFileStreamPath("reference.wav").getAbsolutePath();
        Log.d(TAG, "referenceFilepath: " + referenceFilepath);
        enhancedFilepath = getApplicationContext().getFileStreamPath("enhanced.wav").getAbsolutePath();
        Log.d(TAG, "enhancedFilepath: " + enhancedFilepath);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        requestRecordPermission();
        checkInternetPermission();
        setupVisualizerFxAndUI();

        Toast.makeText(this, "Connecting to websocket", Toast.LENGTH_SHORT).show();
        checkWriteStoragePermission();

        if(webSocket == null) {
            webSocket = new WebSocketClient(cache);
        }

        checkLatency();
        setupIncomingTranscript();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && audioRecord != null) {
            stopRecording();
        }
        koala.delete();
        cheetah.delete();
        webSocket.close();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing() && audioRecord != null) {
            stopRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted, proceed with audio capture

                try {
                    microphoneReader.start();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                audioRecord.startRecording();
            } else {
                // Permission has been denied, show a message to the user or handle the error
                Toast.makeText(this, "Audio capture permission denied", Toast.LENGTH_SHORT).show();
                ToggleButton toggleButton = findViewById(R.id.recordButton);
                toggleButton.toggle();
            }
        }
    }

    private void checkLatency() {
        Timer timer = new Timer();
        int interval = 100;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                errorMessage.setText("Ping: " + webSocket.ping() + "ms");
            }
        },0, interval);
    }

    //---------LISTENERS--------------
    public void onClickConnect(View view) {
        Toast.makeText(this, "Connecting to websocket", Toast.LENGTH_SHORT).show();
        checkWriteStoragePermission();

        if(webSocket == null) {
            webSocket = new WebSocketClient(cache);
        }

    }

    public void onClickRecord(View view) {
        try {
            if (recordButton.isChecked()) {
                stop.set(false);


                if (hasRecordPermission()) {
                    startRecording();
                    microphoneReader.start();
                } else {
                    requestRecordPermission();
                }

            } else {
                stop.set(true);
                microphoneReader.stop();

                // Send audio to server
                if(webSocket != null) {
                    webSocket.sendAudio(enhancedFilepath);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onClickVolumeButton(View view) {
        VolumeControl.ToggleEnabled();
    }

    private void onKoalaInitError(String error) {

        errorMessage.setText(error);

        recordButton.setEnabled(false);
        recordButton.setError("error");
    }

    //--------HELPERS----------

    /**
     * Initialize Koala (Audio supressor)
     */
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

    /**
     * Initialize Cheetah (Real-time speech-to-text)
     */
    private void initCheetah() {
        try {
            cheetah = new Cheetah.Builder()
                    .setAccessKey(ACCESS_KEY)
                    .setModelPath(CHEETAH_MODEL_FILE)
                    .setEnableAutomaticPunctuation(true)
                    .build(getApplicationContext());
        } catch (CheetahInvalidArgumentException e) {
            onKoalaInitError(String.format("%s\nEnsure your AccessKey '%s' is valid", e.getMessage(), ACCESS_KEY));
        } catch (CheetahActivationException e) {
            onKoalaInitError("AccessKey activation error");
        } catch (CheetahActivationLimitException e) {
            onKoalaInitError("AccessKey reached its device limit");
        } catch (CheetahActivationRefusedException e) {
            onKoalaInitError("AccessKey refused");
        } catch (CheetahActivationThrottledException e) {
            onKoalaInitError("AccessKey has been throttled");
        } catch (CheetahException e) {
            onKoalaInitError("Failed to initialize Cheetah " + e.getMessage());
        }
    }

    /**
     * Update the transcript TextView on UI
     * @param transcript transcript to display
     */
    private void updateTranscriptView(String transcript) {
        runOnUiThread(() -> {
            if (transcript.length() != 0) {
                transcriptText.setText(transcript);
            }
        });
    }

    /**
     * Check record audio permission
     * @return  true if have recording permission
     */
    private boolean hasRecordPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request record audio permission
     */
    private void requestRecordPermission() {
        if (hasRecordPermission()) {return;}

        // request audio permission from user
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_RECORD_AUDIO);
    }

    /**
     * Check internet permission and request the permission
     */
    private void checkInternetPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, MY_PERMISSIONS_INTERNET);
        }
    }

    /**
     *  Check WebSocket permission and request the permission
     */
    private void checkWriteStoragePermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
            Log.i("WebSocket", "not granted");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_VIDEO}, MY_PERMISSIONS_READ_MEDIA_VIDEO);
        }else{
            Log.i("WebSocket", "granted");
        }
    }

    /**
     * Start audio recording from microphone
     */
    @SuppressLint("MissingPermission")
    public void startRecording() {
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) ;

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);

        new Thread(new Runnable() {
            @Override
            public void run() {
                short[] buffer = new short[bufferSize];
                audioRecord.startRecording();
                while (!stop.get()) {
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

    /**
     * Stop audio recording input
     */
    public void stopRecording() {
        if (!stop.get()) {
            audioRecord.stop();
            audioRecord.release();
        }
    }



    private void setupIncomingTranscript() {
        Timer timer = new Timer();
        int timerInterval = 50;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if(webSocket != null) {
                        String s = webSocket.getNextTranscript();
                        if (s == null) {
                            return;
                        }
                        Log.d(TAG, "recv: " + s);
                        String[] content = s.split(">");

                        if (isFirst) {
                            usernames.add(content[0]);
                            isFirst = false;
                        }

                        if (isGenerateOtherName.get()) {
                            stringBuilder.append("\n").append(usernames.getFirst()).append(":\n");
                            isGenerateOtherName.set(false);
                            isGenerateYourName.set(true);
                        }

                        stringBuilder.append(content[1]);

                        updateTranscriptView(stringBuilder.toString());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, timerInterval);
    }

    /**
     * Setup wave visualizer
     */
    private void setupVisualizerFxAndUI() {
        // Create a VisualizerView (defined below), which will render the simplified audio
        // wave form to a Canvas.
        //Log.d("MAIN", "Started!");
        Timer timer = new Timer();
        long interval = 50; // 1/20 second in milliseconds

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // This code will be executed every 1/20 second
                try {
                    // outgoing wave
                    beforeProcessWave.updateVisualizer(getAudioData());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, 0, interval);

        Timer timer1 = new Timer();
        long interval1 = 50; // 1/20 second in milliseconds

        timer1.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    int buff = 3776;
                    // incoming wave
                    if (webSocket == null) {
                        afterProcessWave.updateVisualizer((short[]) null);
                    } else {
                        byte[] data = webSocket.getAudio();
                        if (data == null) {
                            afterProcessWave.updateVisualizer((short[]) null);
                            return;
                        }
                        int numLoops = data.length / buff;
                        int start = 0;
                        int end = buff - 1;


                        for (int i = 0; i < numLoops; i++) {

                            // index
                            // 0 -> buffersize - 1
                            // buffersize -> 2 * buffersize - 1
                            // 2 * buffersize -> 3 * buffersize - 1
                            // etc..
                            if (end >= data.length || start < 0) {
                                break;
                            }
                            byte[] dataCopy = Arrays.copyOfRange(data, start, end);
                            afterProcessWave.updateVisualizer(dataCopy);
                            start = end + 1;
                            end += buff ;
                            Thread.sleep(interval1);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, interval1);
    }

    /**
     * Private class that handles all the logic of handling data from microphone and
     * feed it into Cheetah/Koala API
     */
    private class MicrophoneReader {
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean stop = new AtomicBoolean(false);
        private final AtomicBoolean stopped = new AtomicBoolean(false);
        final int wavHeaderLength = 44;
        private RandomAccessFile referenceFile;
        private RandomAccessFile enhancedFile;
        private int totalSamplesWritten;

        /**
         * Start the STT and Sound supressor process
         * @throws IOException exception
         */
        void start() throws IOException {

            if (started.get()) {
                return;
            }

            referenceFile = new RandomAccessFile(referenceFilepath, "rws");
            enhancedFile = new RandomAccessFile(enhancedFilepath, "rws");
            writeWavHeader(referenceFile, (short) 1, (short) 16, 16000, 0);
            writeWavHeader(enhancedFile, (short) 1, (short) 16, 16000, 0);

            started.set(true);

            transcriptText.setText("");

            Executors.newSingleThreadExecutor().submit((Callable<Void>) () -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                read();
                return null;
            });
        }

        /**
         * Stop all ongoing thread and wait for all of them to finish to create a .wav file output
         * @throws InterruptedException
         * @throws IOException
         */
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

        /**
         * Read audio from microphone and process it using API
         * @throws KoalaException sound supressor exception
         * @throws CheetahException STT exception
         */
        @SuppressLint({"MissingPermission", "SetTextI18n", "DefaultLocale"})
        private void read() throws KoalaException, CheetahException {
            final int minBufferSize = AudioRecord.getMinBufferSize(
                    koala.getSampleRate(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            final int bufferSize = Math.max(koala.getSampleRate() / 2, minBufferSize);

            AudioRecord audioRecord = null;

            if (isGenerateYourName.get()) {
                stringBuilder.append("\nYou:\n");
                isGenerateYourName.set(false);
                isGenerateOtherName.set(true);
            }


            short[] frameBuffer = new short[koala.getFrameLength()];
            short[] cheetahFrameBuffer = new short[cheetah.getFrameLength()];

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
                boolean writeCheetah = false;
                int enhancedSamplesWritten = 0;

                while (!stop.get()) {
                    if (audioRecord.read(frameBuffer, 0, frameBuffer.length) == frameBuffer.length) {
                        short[] frameBufferEnhanced = koala.process(frameBuffer);
                        VolumeControl.NormalizeVolume(frameBufferEnhanced);
                        writeFrame(referenceFile, frameBuffer);
                        totalSamplesWritten += frameBuffer.length;
                        if (totalSamplesWritten >= koalaDelay) {
                            writeFrame(enhancedFile, frameBufferEnhanced);
                            enhancedSamplesWritten += frameBufferEnhanced.length;
                        }

                        // Copy the buffered contents to the cheetah buffer
                        int offset = writeCheetah ? frameBufferEnhanced.length: 0;
                        System.arraycopy(frameBufferEnhanced, 0, cheetahFrameBuffer, offset, frameBufferEnhanced.length);

                        if (writeCheetah) {
                            CheetahTranscript transcriptObj = cheetah.process(cheetahFrameBuffer);
                            String newString = transcriptObj.getTranscript();

                            if (!newString.equals("")) {
                                webSocket.sendTranscript(newString);
                                stringBuilder.append(newString);
                                // updateTranscriptView(newString);
                            }

                            updateTranscriptView(stringBuilder.toString());
                        }

                        writeCheetah = !writeCheetah;
                    }

                    if ((totalSamplesWritten / koala.getFrameLength()) % 10 == 0) {
                        runOnUiThread(() -> {
                            double secondsRecorded = ((double) (totalSamplesWritten) / (double) (koala.getSampleRate()));
                            recordedText.setText(String.format("Recording: %.1fs", secondsRecorded));
                        });
                    }
                }

                final CheetahTranscript transcriptObj = cheetah.flush();
                webSocket.sendTranscript(transcriptObj.getTranscript());
                stringBuilder.append(transcriptObj.getTranscript());
                updateTranscriptView(stringBuilder.toString());
                //updateTranscriptView(transcriptObj.getTranscript());

                audioRecord.stop();

                runOnUiThread(() -> {
                    double secondsRecorded = ((double) (totalSamplesWritten) / (double) (koala.getSampleRate()));
                    recordedText.setText(String.format("Recorded: %.1fs", secondsRecorded));
                });

                short[] emptyFrame = new short[koala.getFrameLength()];
                Arrays.fill(emptyFrame, (short) 0);
                while (enhancedSamplesWritten < totalSamplesWritten) {
                    short[] frameBufferEnhanced = koala.process(emptyFrame);
                    VolumeControl.NormalizeVolume(frameBufferEnhanced);
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

        /**
         * Write a frame to an output file
         * @param outputFile a file to write to
         * @param frame data to write to the file
         * @throws IOException io exception
         */
        private void writeFrame(RandomAccessFile outputFile, short[] frame) throws IOException {
            ByteBuffer byteBuf = ByteBuffer.allocate(2 * frame.length);
            byteBuf.order(ByteOrder.LITTLE_ENDIAN);

            for (short s : frame) {
                byteBuf.putShort(s);
            }
            outputFile.write(byteBuf.array());
        }

        /**
         * Write .wav header to an output file
         * @param outputFile a file to write to
         * @param channelCount number of channel
         * @param bitDepth depth of bit
         * @param sampleRate rate of sample
         * @param totalSampleCount total sample number
         * @throws IOException io exception
         */
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