package com.demo.headpose;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

public class MyImageUtils {

    //用于人脸框裁剪
    public static Bitmap cropImageFromRect(Bitmap inputImage, Rect rect) {
        // Ensure the rectangle is within bounds
        int left = Math.max(rect.left, 0);
        int top = Math.max(rect.top, 0);
        int width = Math.min(rect.width(), inputImage.getWidth() - rect.left);
        int height = Math.min(rect.height(), inputImage.getHeight() - rect.top);

        // Crop the image
        Bitmap croppedBitmap = Bitmap.createBitmap(inputImage, left, top, width, height);

        return croppedBitmap;
    }

    //由欧拉角绘制坐标轴
    public static void drawAxis(Bitmap drawBitmap, float yaw, float pitch, float roll, float centerX, float centerY, float size, float thick) {
        // 创建一个 Canvas 对象
        Canvas canvas = new Canvas(drawBitmap);
        // 创建一个画笔对象
        Paint paint = new Paint();
        paint.setStrokeWidth(thick);

        yaw = (float) -(yaw * Math.PI / 180);
        pitch = (float) (pitch * Math.PI / 180);
        roll = (float) (roll * Math.PI / 180);

        // X 轴
        float x1 = size * (float) (Math.cos(yaw) * Math.cos(roll)) + centerX;
        float y1 = size * (float) (Math.cos(pitch) * Math.sin(roll) + Math.cos(roll) * Math.sin(pitch) * Math.sin(yaw)) + centerY;
        // Y 轴
        float x2 = (float) (size * (-Math.cos(yaw) * Math.sin(roll)) + centerX);
        float y2 = size * (float) (Math.cos(pitch) * Math.cos(roll) - Math.sin(pitch) * Math.sin(yaw) * Math.sin(roll)) + centerY;
        // Z 轴
        float x3 = size * (float) (Math.sin(yaw)) + centerX;
        float y3 = (float) (size * (-Math.cos(yaw) * Math.sin(pitch)) + centerY);

        // 绘制 X 轴 （红色）
        paint.setColor(Color.RED);
        canvas.drawLine(centerX, centerY, x1, y1, paint);
        // 绘制 Y 轴 （绿色）
        paint.setColor(Color.GREEN);
        canvas.drawLine(centerX, centerY, x2, y2, paint);
        // 绘制 Z 轴 （蓝色）
        paint.setColor(Color.BLUE);
        canvas.drawLine(centerX, centerY, x3, y3, paint);
    }
}
