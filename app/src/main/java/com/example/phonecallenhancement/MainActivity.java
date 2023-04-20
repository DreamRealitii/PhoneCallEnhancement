package com.example.phonecallenhancement;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class MainActivity extends AppCompatActivity {

    // CONSTANTS
    public static int MY_PERMISSIONS_RECORD_AUDIO = 1;
    public static int MY_PERMISSIONS_INTERNET = 2;

    // UI COMPONENTS
    private Button startService, pauseService, stopService;
    private ActionBar actionBar;
    private AudioRecord audioRecord;
    private VisualizerView BeforeProcessWave, AfterProcessWave;

    // FIELDS
    private int bufferSize;
    private BlockingQueue<short[]> audioDataQueue;
    public short[] getAudioData() throws InterruptedException {
        return audioDataQueue.take();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ExecutorService executorService = Executors.newFixedThreadPool(4);


        startService = this.findViewById(R.id.btnStartService);
        pauseService = this.findViewById(R.id.btnPauseService);
        stopService = this.findViewById(R.id.btnStopService);
        BeforeProcessWave = this.findViewById(R.id.beforeWave);
        AfterProcessWave = this.findViewById(R.id.afterWave);

        actionBar = getSupportActionBar();
        actionBar.setTitle("Jancok");
        actionBar.setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.bg, null));
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        checkRecordAudioPermission();
        checkInternetPermission();
        setupVisualizerFxAndUI();
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
                startRecording();
            } else {
                // Permission has been denied, show a message to the user or handle the error
                Toast.makeText(this, "Audio capture permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onButtonClick(View view) {
        Button b = (Button) view;
        if (b.equals(findViewById(R.id.btnStartService))) {
            Toast.makeText(this, "HI! " + b.getText(), Toast.LENGTH_SHORT).show();
//            Example to open web
//            String url = "http://www.twitter.com";
//            Intent i = new Intent(Intent.ACTION_VIEW);
//            i.setData(Uri.parse(url));
//            startActivity(i);
        } else {
            Toast.makeText(this, "You clicked on " + b.getText(), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkRecordAudioPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
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
        long interval = 100; // 1/20 second in milliseconds

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // This code will be executed every 1/20 second
                try {
                    BeforeProcessWave.updateVisualizer(getAudioData());
                    AfterProcessWave.updateVisualizer(getAudioData());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, 0, interval);

    }
}