package com.example.phonecallenhancement;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.shapes.OvalShape;
import android.media.audiofx.Visualizer;
import android.os.Debug;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import Sound.VolumeControl;

/**
 * A simple class that draws dialates the circle on input
 */
public class BubbleView extends View {
    private static final String TAG = "Debugging";
    private int maxSize;
    private float size;

    private final RectF oval = new RectF();
    private Paint paint;

    public BubbleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        maxSize = getHeight();
        size = maxSize;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
    }

    public void updateVisualizer(short[] shorts) {
        if (shorts == null) {
            return;
        }
        float max = shorts[0];
        for (short num : shorts) {
            max = max < num ? num : max;
        }
        size = maxSize * (max / Short.MAX_VALUE + 1) / 2;
        invalidate();
    }

    public void updateVisualizer(byte[] bytes) {
        if (bytes == null) {
            return;
        }
        short[] shorts = bytesToShorts(bytes);
        updateVisualizer(shorts);
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
        oval.set(0, 0, size, size);
        canvas.drawOval(oval, paint);
    }
}