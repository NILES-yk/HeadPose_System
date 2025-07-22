package com.demo.headpose;

import android.content.Context;
import android.graphics.Bitmap;

import org.pytorch.IValue;
import org.pytorch.MemoryFormat;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HeadPose {

    Module headPose = null;
    Bitmap resizedBitmap;
    Tensor inputTensor;
    Tensor outputTensor;

    //定义HeadPose实例时设置加载的模型
    public HeadPose(Context context, String modelName) throws IOException {
        String modelPath = assetFilePath(context, modelName);
        headPose = Module.load(modelPath);

    }

    //将输入图片缩放、归一化、标准化（根据模型所需输入而变），运行模型推理
    public float[] InferFromBitmap(Bitmap inputBitmap) {
        resizedBitmap = Bitmap.createScaledBitmap(inputBitmap, 224, 224, true);
        inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB,
                MemoryFormat.CHANNELS_LAST);
        outputTensor = headPose.forward(IValue.from(inputTensor)).toTensor();
        float[] rotation = outputTensor.getDataAsFloatArray();

        return rotationToEuler(rotation);
    }

    //从3×3旋转矩阵获得欧拉角
    public float[] rotationToEuler(float[] rotation) {
        float[] degree = { 0, 0, 0 };
        float sy = (float) Math.sqrt(rotation[0] * rotation[0] + rotation[3] * rotation[3]);
        if (sy > 1e-6) {
            degree[0] = (float) Math.toDegrees(Math.atan2(-rotation[6], sy));
            degree[1] = (float) Math.toDegrees(Math.atan2(rotation[7], rotation[8]));
            degree[2] = (float) Math.toDegrees(Math.atan2(rotation[3], rotation[0]));
        } else {
            degree[0] = (float) Math.toDegrees(Math.atan2(-rotation[6], sy));
            degree[1] = (float) Math.toDegrees(Math.atan2(-rotation[5], rotation[4]));
            degree[2] = 0;
        }

        return degree;
    }

    /**
     * Copies specified asset to the file in /files app directory and returns this file absolute path.
     * @return absolute file path
     */
    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}
