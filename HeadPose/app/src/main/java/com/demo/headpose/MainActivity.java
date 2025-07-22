package com.demo.headpose;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA}; // 所需权限：摄像头权限
    private static final String TAG = "HeadPose"; // 日志标签

    private PreviewView previewView;       // CameraX 的预览界面
    private ExecutorService cameraExecutor; // 执行图像分析的线程池
    private HeadPose headPose;             // 头姿估计模块（PyTorch模型）
    private SocketManager socketManager;   // 用于网络通信的Socket管理器
    private ImageView imageView;           // 显示处理后图像的组件

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 加载布局文件

        previewView = findViewById(R.id.previewView);   // 获取预览界面控件
        imageView = findViewById(R.id.imageView);       // 获取显示图像控件

        // 初始化头姿估计模型（从assets中加载模型）
        try {
            headPose = new HeadPose(this, "_epoch_80_3.pt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 检查摄像头权限
        if (allPermissionsGranted()) {
            startCamera(); // 权限已授予，启动摄像头
        } else {
            // 请求权限
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // 创建单线程线程池，用于图像处理
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 初始化并启动 Socket 通信模块
        socketManager = new SocketManager();
        socketManager.startServer();
    }

    /**
     * 检查是否所有必要权限都已授予
     */
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false; // 有权限未授予
            }
        }
        return true; // 所有权限已授予
    }

    /**
     * 启动摄像头并绑定预览与图像分析功能
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        // 当CameraProvider准备就绪时执行
        cameraProviderFuture.addListener(() -> {
            try {
                // 获取CameraProvider实例
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // 创建并配置预览用例
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // 创建图像分析用例（设置分辨率和策略）
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // 丢弃旧帧，只保留最新帧
                        .setTargetResolution(new Size(480, 360)) // 设置较低分辨率以加快处理速度
                        .build();

                // 设置图像分析器：FaceAnalyzer 是自定义类，内部实现人脸检测 + 头姿估计
                imageAnalysis.setAnalyzer(cameraExecutor, new FaceAnalyzer(
                        cameraExecutor,
                        headPose,
                        socketManager,
                        // 处理后的图像通过回调设置到 UI 上
                        rotatedBitmap -> runOnUiThread(() -> imageView.setImageBitmap(rotatedBitmap))
                ));

                // 设置为前置摄像头
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // 解绑所有已有用例，重新绑定
                cameraProvider.unbindAll();

                // 绑定预览和图像分析功能到生命周期
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e); // 绑定失败，打印错误日志
            }
        }, ContextCompat.getMainExecutor(this)); // 在主线程执行回调
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();       // 关闭线程池
        socketManager.closeConnection(); // 关闭Socket连接
    }

    /**
     * 权限请求结果回调
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera(); // 权限已获取，启动摄像头
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                finish(); // 权限被拒绝，关闭应用
            }
        }
    }
}
