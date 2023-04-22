package com.example.phonecallenhancement;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;

import com.aykuttasil.callrecord.CallRecord;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CallRecord callRecord = new CallRecord.Builder(this)
                .setLogEnable(true)
                .setRecordFileName("RecordFileName")
                .setRecordDirName("RecordDirName")
                .setRecordDirPath(Environment.getExternalStorageDirectory().getPath()) // optional & default value
                .setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB) // optional & default value
                .setOutputFormat(MediaRecorder.OutputFormat.AMR_NB) // optional & default value
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION) // optional & default value
                .setShowSeed(true) // optional & default value ->Ex: RecordFileName_incoming.amr || RecordFileName_outgoing.amr
                .build();

        System.out.println(Environment.getExternalStorageDirectory().getPath());
        callRecord.startCallReceiver();
    }
}