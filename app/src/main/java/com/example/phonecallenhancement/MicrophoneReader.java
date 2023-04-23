package com.example.phonecallenhancement;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.picovoice.koala.Koala;
import ai.picovoice.koala.KoalaException;

// PRIVATE CLASS
public class MicrophoneReader {
    public static String TAG = "Romero";
    private Activity activity;
    private TextView textView;
    private String referenceFilepath, enhancedFilepath;
    private Koala koala;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private RandomAccessFile referenceFile;
    private RandomAccessFile enhancedFile;
    private int totalSamplesWritten;
    private final MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
    private final MediaFormat mediaFormat ;
    private final MediaCodec mediaCodec;
    private MediaCodec.BufferInfo outputBufferInfo;
    long usec;

    MediaFormat outputFormat;

    public MicrophoneReader(Koala koala, Activity activity,
                            TextView textView, String referenceFilepath, String enhancedFilepath) {
        this.koala = koala;
        this.activity = activity;
        this.textView = textView;
        this.referenceFilepath = referenceFilepath;
        this.enhancedFilepath = enhancedFilepath;
        this.mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC,
                koala.getSampleRate(), 1);
        try {
            String format = mediaCodecList.findEncoderForFormat(mediaFormat);
            this.mediaCodec = MediaCodec.createByCodecName(format);
            Log.d(TAG, "mediaCodec: " + mediaCodec.getCanonicalName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.outputBufferInfo = new MediaCodec.BufferInfo();
        this.usec = 1000000000L * koala.getFrameLength()/ koala.getSampleRate();
    }

    void start() throws IOException {
        if (started.get()) {
            return;
        }

        referenceFile = new RandomAccessFile(referenceFilepath, "rws");
        enhancedFile = new RandomAccessFile(enhancedFilepath, "rws");
//            writeWavHeader(referenceFile, (short) 1, (short) 16, 16000, 0);
//            writeWavHeader(enhancedFile, (short) 1, (short) 16, 16000, 0);

        started.set(true);
        mediaCodec.configure(mediaFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        outputFormat = mediaCodec.getOutputFormat();

        outputBufferInfo.set(0, koala.getFrameLength() * 2, usec, 0);

        mediaCodec.start();


        Log.d(TAG, "start thread: ");
        Executors.newSingleThreadExecutor().submit((Callable<Void>) () -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            Log.d(TAG, "in thread: ");
            read();
            return null;
        });
    }

    void stop() throws InterruptedException, IOException {
        Log.d(TAG, "in stop: ");
        if (!started.get()) {
            return;
        }

        stop.set(true);

        while (!stopped.get()) {
            Thread.sleep(10);
        }

//            writeWavHeader(referenceFile, (short) 1, (short) 16, 16000, totalSamplesWritten);
//            writeWavHeader(enhancedFile, (short) 1, (short) 16, 16000, totalSamplesWritten);
        referenceFile.close();
        enhancedFile.close();
        mediaCodec.stop();

        started.set(false);
        stop.set(false);
        stopped.set(false);
    }

    public byte[] shortToByte(short[] arr) {
        ByteBuffer bb = ByteBuffer.allocate(arr.length * 2);
        bb.asShortBuffer().put(arr);
        return bb.array(); // this returns the "raw" array, it's shared and not copied!
    }

    private int encodeAndWrite(short[] dataBuffer, RandomAccessFile outputFile) throws IOException {
        int inputBufferCapacity = -1;
        int inputBufferId = mediaCodec.dequeueInputBuffer(1000);
        if (inputBufferId >= 0) {
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferId);
            int start = inputBuffer.position();
            byte[] data = shortToByte(dataBuffer);
            // fill inputBuffer with valid data
            inputBuffer.put(data);

            inputBufferCapacity = data.length;

            // TODO:
            int end = inputBuffer.position();
            Log.d(TAG, "end - start: " + (end - start));
            inputBuffer.position(inputBuffer.limit());
            mediaCodec.queueInputBuffer(inputBufferId, start, end - start, usec, 0);
        }

        int outputBufferId = mediaCodec.dequeueOutputBuffer(outputBufferInfo, 1000);
        Log.d(TAG, "outputBufferId: " + outputBufferId);
        if (outputBufferId >= 0) {
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferId);
            MediaFormat bufferFormat = mediaCodec.getOutputFormat(outputBufferId);
            // bufferFormat is identical to outputFormat
            Log.d(TAG, "bufferFormat == outputFormat: " + Objects.equals(bufferFormat, outputFormat));
            // outputBuffer is ready to be processed or rendered.

            // outputFile.write(outputBuffer.array());

            if (outputBuffer.hasArray()) {
                outputFile.write(outputBuffer.array(), outputBuffer.arrayOffset(),
                        outputBuffer.remaining());
            } else {
                final byte[] b = new byte[outputBuffer.remaining()];
                outputBuffer.duplicate().get(b);
                outputFile.write(b);
            }

            mediaCodec.releaseOutputBuffer(outputBufferId, false);
        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = mediaCodec.getOutputFormat(); // option B
        }
        return inputBufferCapacity;
    }

    @SuppressLint({"MissingPermission", "SetTextI18n", "DefaultLocale"})
    private void read() throws KoalaException {
        Log.d(TAG, "in read: ");
        final int minBufferSize = AudioRecord.getMinBufferSize(
                koala.getSampleRate(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        final int bufferSize = Math.max(koala.getSampleRate() / 2, minBufferSize);

        AudioRecord audioRecord = null;

        short[] frameBuffer = new short[koala.getFrameLength()];
        Log.d(TAG, "frameLength: " + koala.getFrameLength());

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

            //---------------------------------------------------------------
            // TODO: Do i need to create a new codec / reset it everytime i call encodeAndWrite()?
            while(!stop.get()) {
                Log.d(TAG, "in while: ");
                if (audioRecord.read(frameBuffer, 0, frameBuffer.length) == frameBuffer.length) {
                    final short[] frameBufferEnhanced = koala.process(frameBuffer);
                    int referenceWriteAmt = encodeAndWrite(frameBuffer, referenceFile);
                    mediaCodec.flush();
                    if (referenceWriteAmt == -1) {
                        Log.e(TAG, "read: reference input buffer capacity == -1");
                    }
                    totalSamplesWritten += referenceWriteAmt;


                    if (totalSamplesWritten >= koalaDelay) {
                        int enhancedWriteAmt = encodeAndWrite(frameBufferEnhanced, enhancedFile);
                        mediaCodec.flush();
                        if (enhancedWriteAmt == -1) {
                            Log.e(TAG, "read: enhanced input buffer capacity == -1");
                        }
                        enhancedSamplesWritten += enhancedWriteAmt;
                    }
                }

                if ((totalSamplesWritten / koala.getFrameLength()) % 10 == 0) {
                    activity.runOnUiThread(() -> {
                        double secondsRecorded = ((double) (totalSamplesWritten) / (double) (koala.getSampleRate()));
                        textView.setText(String.format("Recording: %.1fs", secondsRecorded));
                    });
                }
            }

            audioRecord.stop();

            activity.runOnUiThread(() -> {
                double secondsRecorded = ((double) (totalSamplesWritten) / (double) (koala.getSampleRate()));
                textView.setText(String.format("Recorded: %.1fs", secondsRecorded));
            });

            short[] emptyFrame = new short[koala.getFrameLength()];
            Arrays.fill(emptyFrame, (short) 0);
            while (enhancedSamplesWritten < totalSamplesWritten) {
                final short[] frameBufferEnhanced = koala.process(emptyFrame);
                enhancedSamplesWritten += encodeAndWrite(frameBufferEnhanced, enhancedFile);
                mediaCodec.flush();
            }

            //---------------------------------------------------------------

//                while (!stop.get()) {
//                    if (audioRecord.read(frameBuffer, 0, frameBuffer.length) == frameBuffer.length) {
//                        final short[] frameBufferEnhanced = koala.process(frameBuffer);
//
//                        writeFrame(referenceFile, frameBuffer);
//                        totalSamplesWritten += frameBuffer.length;
//                        if (totalSamplesWritten >= koalaDelay) {
//                            writeFrame(enhancedFile, frameBufferEnhanced);
//                            enhancedSamplesWritten += frameBufferEnhanced.length;
//                        }
//                    }
//
//                    if ((totalSamplesWritten / koala.getFrameLength()) % 10 == 0) {
//                        runOnUiThread(() -> {
//                            double secondsRecorded = ((double) (totalSamplesWritten) / (double) (koala.getSampleRate()));
//                            recordedText.setText(String.format("Recording: %.1fs", secondsRecorded));
//                        });
//                    }
//                }
//
//                audioRecord.stop();
//
//                runOnUiThread(() -> {
//                    double secondsRecorded = ((double) (totalSamplesWritten) / (double) (koala.getSampleRate()));
//                    recordedText.setText(String.format("Recorded: %.1fs", secondsRecorded));
//                });
//
//                short[] emptyFrame = new short[koala.getFrameLength()];
//                Arrays.fill(emptyFrame, (short) 0);
//                while (enhancedSamplesWritten < totalSamplesWritten) {
//                    final short[] frameBufferEnhanced = koala.process(emptyFrame);
//                    writeFrame(enhancedFile, frameBufferEnhanced);
//                    enhancedSamplesWritten += frameBufferEnhanced.length;
//                }
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            throw new KoalaException(e);
        } finally {
            if (audioRecord != null) {
                audioRecord.release();
            }
            mediaCodec.stop();
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

}
