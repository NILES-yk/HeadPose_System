package com.demo.headpose;

import android.graphics.*;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

/**
 * 用于图像分析的人脸检测与头姿估计分析器类。
 * 实现了 CameraX 的 ImageAnalysis.Analyzer 接口。
 */
public class FaceAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = "FaceAnalyzer";

    // MLKit 人脸检测器
    private final FaceDetector detector;

    // 用于执行异步任务的线程池
    private final ExecutorService executorService;

    // 自定义的头姿估计类
    private final HeadPose headPose;

    // 发送头姿数据的Socket管理器
    private final SocketManager socketManager;

    // 用于回调将绘制好姿态的 Bitmap 传回主线程更新UI
    private final FaceAnalyzerCallback callback;

    // 控制是否处理帧，避免同时处理多帧
    private volatile boolean isProcessingFrame = false;

    /**
     * 回调接口：将处理后Bitmap返回给调用者（通常是 UI）
     */
    public interface FaceAnalyzerCallback {
        void onBitmapReady(Bitmap bitmap);
    }

    /**
     * 构造函数
     */
    public FaceAnalyzer(ExecutorService executorService, HeadPose headPose, SocketManager socketManager, FaceAnalyzerCallback callback) {
        this.executorService = executorService;
        this.headPose = headPose;
        this.socketManager = socketManager;
        this.callback = callback;

        // 配置人脸检测器参数
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // 快速模式
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)        // 检测五官关键点
                .build();
        detector = FaceDetection.getClient(options);
    }

    /**
     * CameraX每帧图像的分析处理回调
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (isProcessingFrame) {
            imageProxy.close(); // 若当前已有帧在处理，则跳过
            return;
        }

        isProcessingFrame = true;

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            isProcessingFrame = false;
            return;
        }

        // 将 YUV 图像转换为 Bitmap
        Bitmap fullBitmap = imageToBitmap(imageProxy);
        // 左右镜像（适配前置摄像头）
        Bitmap mutableBitmap = mirrorBitmap(fullBitmap);

        // 将 Bitmap 封装成 InputImage 用于 MLKit 识别
        InputImage image = InputImage.fromBitmap(mutableBitmap, 0);

        // 执行人脸检测
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.size() > 0) {
                        // 取第一张脸
                        Face face = faces.get(0);
                        Rect bounds = face.getBoundingBox();

                        // 创建 Canvas 以绘制到 bitmap 上
                        Canvas canvas = new Canvas(mutableBitmap);

                        // 裁剪人脸区域用于头姿推理
                        Bitmap cropped = MyImageUtils.cropImageFromRect(mutableBitmap, bounds);

                        // 异步处理头姿估计
                        executorService.execute(() -> {
                            // 获取眼睛关键点
                            FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
                            FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);

                            if (leftEye != null && rightEye != null) {
                                // 调用模型预测头姿角度：yaw, pitch, roll
                                float[] degree = headPose.InferFromBitmap(cropped);

                                PointF left = leftEye.getPosition();
                                PointF right = rightEye.getPosition();

                                // 绘制头姿角度与辅助坐标轴
                                Paint mPaint = new Paint();
                                mPaint.setStyle(Paint.Style.STROKE);
                                mPaint.setStrokeWidth(2);
                                mPaint.setTextSize(20);

                                mPaint.setColor(Color.RED);
                                canvas.drawRect(bounds, mPaint); // 绘制人脸框
                                canvas.drawText("Yaw: " + String.format("%.1f", degree[0]), bounds.left, bounds.top, mPaint);

                                mPaint.setColor(Color.BLUE);
                                canvas.drawText("Pitch: " + String.format("%.1f", degree[1]), bounds.left, bounds.top + 50, mPaint);

                                mPaint.setColor(Color.GREEN);
                                canvas.drawText("Roll: " + String.format("%.1f", degree[2]), bounds.left, bounds.top + 100, mPaint);

                                // 绘制姿态坐标轴辅助线
                                MyImageUtils.drawAxis(mutableBitmap, degree[0], degree[1], degree[2],
                                        bounds.left + bounds.width() / 2, bounds.top + bounds.height() / 2,
                                        150, 5);

                                // 旋转90度（适配）
                                Bitmap rotatedBitmap = rotateBitmap(mutableBitmap, 90);

                                // 回调给主线程用于UI显示
                                callback.onBitmapReady(rotatedBitmap);

                                // 通过socket发送数据
                                socketManager.sendData(degree[0], degree[1], degree[2], left.x, left.y, right.x, right.y);
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Face detection failed", e))
                .addOnCompleteListener(t -> {
                    imageProxy.close();
                    isProcessingFrame = false;
                });
    }

    /**
     * 将 ImageProxy 图像转换为 Bitmap（通过 YUV -> JPEG -> Bitmap）
     */
    private Bitmap imageToBitmap(ImageProxy image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        // 压缩为 JPEG 再解码为 Bitmap
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
        byte[] jpegBytes = out.toByteArray();

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    /**
     * 将 Bitmap 旋转指定角度
     */
    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * 镜像 Bitmap（水平翻转）
     */
    private Bitmap mirrorBitmap(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1); // 水平翻转
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
