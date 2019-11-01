package com.camerakit.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.camerakit.R;

public class CameraFocusView extends View {

    private float lineWidth;
    private int lineColor;
    private Paint calibrationPaint;
    private Context mContext;

    private int mLeft;
    private int mTop;

    public CameraFocusView(Context context, int left, int top) {
        this(context, null);
        mLeft = left;
        mTop = top;
    }

    public CameraFocusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraFocusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraFocusView);
        lineWidth = a.getDimension(R.styleable.CameraFocusView_lineWidth,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics()));
        lineColor = a.getColor(R.styleable.CameraFocusView_lineColor, getResources().getColor(R.color.camera_focus_color));
        a.recycle();

        calibrationPaint = new Paint();
        calibrationPaint.setStrokeWidth(lineWidth);
        calibrationPaint.setColor(lineColor);
        calibrationPaint.setStyle(Paint.Style.FILL);
        calibrationPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int rectWidth = dip2px(mContext, 50);
        //float left = (getWidth() - rectWidth) / 2;
        //float top = (getHeight() - rectWidth) / 2;
        float right = mLeft + rectWidth;
        float bottom = mTop + rectWidth;

        //画上下左右四个水平较准角
        canvas.drawLine(mLeft, mTop, mLeft + rectWidth / 3, mTop, calibrationPaint);
        canvas.drawLine(mLeft, mTop, mLeft, mTop + rectWidth / 3, calibrationPaint);

        canvas.drawLine(mLeft + rectWidth * 2 / 3, mTop, right, mTop, calibrationPaint);
        canvas.drawLine(right, mTop, right, mTop + rectWidth / 3, calibrationPaint);

        canvas.drawLine(mLeft, mTop + rectWidth * 2 / 3, mLeft, bottom, calibrationPaint);
        canvas.drawLine(mLeft, bottom, mLeft + rectWidth / 3, bottom, calibrationPaint);

        canvas.drawLine(mLeft + rectWidth * 2 / 3, bottom, right, bottom, calibrationPaint);
        canvas.drawLine(right, mTop + rectWidth * 2 / 3, right, bottom, calibrationPaint);

    }

    public static int dip2px(Context context, float dpValue) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

}
