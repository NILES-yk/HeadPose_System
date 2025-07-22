package com.demo.headpose;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketManager {

    private static final String TAG = "SocketManager"; // 用于日志输出的标记
    private ServerSocket serverSocket;  // Socket服务器对象
    private Socket clientSocket;        // 连接进来的客户端
    private DataOutputStream outputStream; // 用于发送数据到服务器的输出流
    private boolean isServerRunning = false;

    // 获取本机实际IP地址
    private String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                // 过滤回环接口、虚拟接口等
                if (iface.isLoopback() || !iface.isUp()) continue;

                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) { // 只获取IPv4地址
                        String ip = addr.getHostAddress();
                        Log.i(TAG, "Found IP address: " + ip + " on interface: " + iface.getDisplayName());
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP address", e);
        }
        return null;
    }

    // 启动Socket服务器并等待客户端连接
    public void startServer() {
        if (isServerRunning) {
            Log.i(TAG, "Server is already running");
            return;
        }

        new Thread(() -> {
            try {
                // 先关闭之前的连接
                closeConnection();
                
                // 获取实际IP地址
                String localIp = getLocalIpAddress();
                Log.i(TAG, "Local IP address: " + localIp);
                
                // 创建新的服务器
                serverSocket = new ServerSocket(5000, 0, java.net.InetAddress.getByName("0.0.0.0"));
                isServerRunning = true;
                Log.i(TAG, "Socket server started on port 5000. Waiting for client...");
                Log.i(TAG, "Server IP: " + serverSocket.getInetAddress().getHostAddress());
                Log.i(TAG, "Server is listening on all interfaces (0.0.0.0)");
                Log.i(TAG, "Connect using IP: " + localIp);
                
                while (isServerRunning) {
                    try {
                        clientSocket = serverSocket.accept();
                outputStream = new DataOutputStream(clientSocket.getOutputStream());
                Log.i(TAG, "Client connected: " + clientSocket.getInetAddress());
                        break;
                    } catch (IOException e) {
                        Log.e(TAG, "Error accepting client connection", e);
                        if (!isServerRunning) break;
                        continue;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket server error", e);
                isServerRunning = false;
            }
        }).start();
    }

    // 发送数据到客户端
    public void sendData(float yaw, float pitch, float roll, float lx, float ly, float rx, float ry) {
        new Thread(() -> {
            try {
                if (outputStream != null) {
                    // 格式化消息，确保每个值的精度一致
                    String message = String.format("%.2f %.2f %.2f %.2f %.2f %.2f %.2f", yaw, pitch, roll, lx, ly, rx, ry);

                    // 在消息末尾添加换行符，确保数据分隔
                    message += "\n";

                    // 使用writeBytes避免UTF限制，可以通过getBytes获取字节数据发送
                    outputStream.write(message.getBytes());
                    outputStream.flush();
                } else {
                    Log.w(TAG, "Client not connected yet. Cannot send data.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Send failed", e);
            }
        }).start();
    }

    // 关闭Socket连接
    public void closeConnection() {
        isServerRunning = false;
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (clientSocket != null) {
                clientSocket.close();
                clientSocket = null;
            }
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
            Log.i(TAG, "Socket connection closed.");
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
    }
}
