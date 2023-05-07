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
        mShorts = shorts;
        invalidate();
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