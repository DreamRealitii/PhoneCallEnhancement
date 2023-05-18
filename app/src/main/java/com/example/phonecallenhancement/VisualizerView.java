package com.example.phonecallenhancement;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.view.View;

/**
 * A simple class that draws waveform data received from a
 * {@link Visualizer.OnDataCaptureListener#onWaveFormDataCapture }
 */
public class VisualizerView extends View {
    private static final String TAG = "Debugging";
    private short[] mShorts;
    private float[] mPoints;
    private final Rect mRect = new Rect();
    private final Paint mForePaint = new Paint();

    public VisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mShorts = null;
        mForePaint.setStrokeWidth(1f);
        mForePaint.setAntiAlias(true);
        mForePaint.setColor(Color.rgb(0, 128, 255));
    }

    public void updateVisualizer(short[] shorts) {
        if (shorts == null) {
            return;
        }
        mShorts = shorts;
        invalidate();
    }

    public void updateVisualizer(byte[] bytes) {
        if (bytes == null) {
            return;
        }
        mShorts = bytesToShorts(bytes);
        invalidate();
    }

    public static short[] bytesToShorts(byte[] byteData) {
        if (byteData == null) {
            return null;
        }
        short[] shortData = new short[byteData.length];

        byte min = Byte.MIN_VALUE;
        byte max = Byte.MAX_VALUE;
        short newMin = Short.MIN_VALUE;
        short newMax = Short.MAX_VALUE;

        for (int i = 0; i < byteData.length; i++) {
            shortData[i] = (short) ((byteData[i] - min) * (newMax - newMin) / (max - min) + newMin);
        }

        return shortData;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mShorts == null) {
            return;
        }
        if (mPoints == null || mPoints.length < mShorts.length * 4) {
            mPoints = new float[mShorts.length * 4];
        }
        mRect.set(0, 0, getWidth(), getHeight());
        for (int i = 0; i < mShorts.length - 1; i++) {
            mPoints[i * 4] = mRect.width() * i / (float) (mShorts.length - 1);
            mPoints[i * 4 + 1] = mRect.height() / 2f - mShorts[i] / (float) Short.MAX_VALUE * mRect.height() / 2f;
            mPoints[i * 4 + 2] = mRect.width() * (i + 1) / (float) (mShorts.length - 1);
            mPoints[i * 4 + 3] = mRect.height() / 2f - mShorts[i + 1] / (float) Short.MAX_VALUE * mRect.height() / 2f;
        }
        canvas.drawLines(mPoints, mForePaint);
    }
}