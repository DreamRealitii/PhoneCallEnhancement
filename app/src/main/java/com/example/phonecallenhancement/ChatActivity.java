package com.example.phonecallenhancement;

import static android.util.Base64.URL_SAFE;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

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
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.phonecallenhancement.databinding.ActivityChatBinding;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import Sound.VolumeControl;
import ai.picovoice.cheetah.Cheetah;
import ai.picovoice.cheetah.CheetahActivationException;
import ai.picovoice.cheetah.CheetahActivationLimitException;
import ai.picovoice.cheetah.CheetahActivationRefusedException;
import ai.picovoice.cheetah.CheetahActivationThrottledException;
import ai.picovoice.cheetah.CheetahException;
import ai.picovoice.cheetah.CheetahInvalidArgumentException;
import ai.picovoice.cheetah.CheetahTranscript;
import ai.picovoice.koala.Koala;
import ai.picovoice.koala.KoalaActivationException;
import ai.picovoice.koala.KoalaActivationLimitException;
import ai.picovoice.koala.KoalaActivationRefusedException;
import ai.picovoice.koala.KoalaActivationThrottledException;
import ai.picovoice.koala.KoalaException;
import ai.picovoice.koala.KoalaInvalidArgumentException;

public class ChatActivity extends AppCompatActivity {

    // UI
    private ActivityChatBinding binding;
    private List<ChatMessage> messages;
    private ChatAdapter adapter;

    // WEB SOCKET
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
    private BlockingQueue<short[]> audioDataQueue = new LinkedBlockingQueue<short[]>();;
    public short[] getAudioData() throws InterruptedException {
        return audioDataQueue.take();
    }
    private String referenceFilepath;
    private String enhancedFilepath;
    private MicrophoneReader microphoneReader;

    private AudioRecord audioRecord;
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private File cache;

    private float volumeSize;
    StringBuilder userSB;

    // CONSTANTS
    public static int MY_PERMISSIONS_RECORD_AUDIO = 1;
    public static int MY_PERMISSIONS_INTERNET = 2;
    public static int MY_PERMISSIONS_READ_MEDIA_VIDEO = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getSupportActionBar().hide();

        cache = getCacheDir();

        volumeSize = binding.layoutVolume.getLayoutParams().width;

        messages = new ArrayList<>();
        adapter = new ChatAdapter(
                "User",
                messages
        );
        binding.chatRecyclerView.setAdapter(adapter);

        microphoneReader = new MicrophoneReader();
        userSB = new StringBuilder();

        referenceFilepath = getApplicationContext().getFileStreamPath("reference.wav").getAbsolutePath();
        enhancedFilepath = getApplicationContext().getFileStreamPath("enhanced.wav").getAbsolutePath();

        checkRecordAudioPermission();
        checkInternetPermission();
        checkWriteStoragePermission();

        initKoala();
        initCheetah();

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setupVisualizerFxAndUI();

        Toast.makeText(this, "Connecting to websocket", Toast.LENGTH_SHORT).show();

        if(webSocket == null) {
            webSocket = new WebSocketClient(cache);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && audioRecord != null) {
            stopRecording();
        }
        koala.delete();
        cheetah.delete();
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

    //---------LISTENERS--------------

    public void onClickRecord(View view) {
        try {
            if (binding.recordButton.isChecked()) {
                stop.set(false);


                if (hasRecordPermission()) {
                    startRecording();
                    microphoneReader.start();
                } else {
                    checkRecordAudioPermission();
                }

            } else {
                stop.set(true);
                microphoneReader.stop();

                // Send audio to server
                if(webSocket != null) {
                    webSocket.sendAudio(enhancedFilepath);
                }

                MakeMessage();

            }
        } catch (Exception e) {
            e.printStackTrace();
            // Toast.makeText(this, "Audio stop command interrupted\n", Toast.LENGTH_SHORT).show();
        }
    }

    private void MakeMessage() {
        String content = binding.inputMessage.getText().toString();
        ChatMessage message = new ChatMessage();
        message.message = content;
        message.sender = "User";
        messages.add(message);
        adapter.notifyItemInserted(messages.size());
        // set the remaining part
        runOnUiThread(() -> binding.inputMessage.setText(""));
    }

    public void onClickVolumeButton(View view) {
        VolumeControl.ToggleEnabled();
    }

    private void onKoalaInitError(String error) {
         Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
    }

    //--------HELPERS----------

    private String wavToBase64(String path) {

        byte[] bytes = new byte[0];

        try {
            bytes = Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String encoded = Base64.encodeToString(bytes, URL_SAFE);

        return encoded;
    }

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

    private void updateTranscriptView(String transcript) {
        runOnUiThread(() -> {
            binding.inputMessage.append(transcript);
        });
    }

    private boolean hasRecordPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkRecordAudioPermission() {
        if (!hasRecordPermission()) {
            // Permission has not been granted yet, request it from the user
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_RECORD_AUDIO);
        }
    }

    private void checkInternetPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, MY_PERMISSIONS_INTERNET);
        }
    }

    private void checkWriteStoragePermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
            Log.i("WebSocket", "not granted");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_VIDEO}, MY_PERMISSIONS_READ_MEDIA_VIDEO);
        }else{
            Log.i("WebSocket", "granted");
        }
    }

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

    public void stopRecording() {
        if (!stop.get()) {
            audioRecord.stop();
            audioRecord.release();
        }
    }

    private void setupVisualizerFxAndUI() {
        // Create a VisualizerView (defined below), which will render the simplified audio
        // wave form to a Canvas.

        Timer timer = new Timer();
        long interval = 100; // 1/10 second in milliseconds
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // This code will be executed every 1/20 second
                try {
                    binding.layoutVolume.updateVisualizer(getAudioData());
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

        public int minBufferSize;
        public int bufferSize;

        void start() throws IOException {

            if (started.get()) {
                return;
            }

            minBufferSize = AudioRecord.getMinBufferSize(
                    koala.getSampleRate(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            bufferSize = Math.max(koala.getSampleRate() / 2, minBufferSize);

            referenceFile = new RandomAccessFile(referenceFilepath, "rws");
            enhancedFile = new RandomAccessFile(enhancedFilepath, "rws");
            writeWavHeader(referenceFile, (short) 1, (short) 16, 16000, 0);
            writeWavHeader(enhancedFile, (short) 1, (short) 16, 16000, 0);

            started.set(true);

            binding.inputMessage.setText("");

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
        private void read() throws KoalaException, CheetahException {


            AudioRecord audioRecord = null;

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
                                updateTranscriptView(newString);
                                Log.d("Cheetah", newString);
                            }

                            if (transcriptObj.getIsEndpoint()) {
                                transcriptObj = cheetah.flush();
                                updateTranscriptView(transcriptObj.getTranscript() + " ");
                            }
                        }
                        writeCheetah = !writeCheetah;
                    }
                }

                final CheetahTranscript transcriptObj = cheetah.flush();
                updateTranscriptView(transcriptObj.getTranscript());

                audioRecord.stop();

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