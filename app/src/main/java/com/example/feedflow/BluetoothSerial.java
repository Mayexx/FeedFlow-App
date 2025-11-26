package com.example.feedflow;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothSerial {

    public interface Callback { void onReceive(byte[] data); }

    private final Context context;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Callback callback;
    private Thread readThread;
    private boolean isReading = false;

    // Standard SPP UUID
    private final UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public BluetoothSerial(Context context) {
        this.context = context; // just store context, don't extend it
    }

    public void connect(String macAddress) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                Log.e("BT", "Bluetooth not available or not enabled");
                return;
            }

            BluetoothDevice device = adapter.getRemoteDevice(macAddress);
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e("BT", "Missing BLUETOOTH_CONNECT permission");
                return;
            }

            socket = device.createRfcommSocketToServiceRecord(SERIAL_UUID);
            socket.connect();

            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();

            startReading();
            Log.d("BT", "Connected to ESP32: " + macAddress);

        } catch (IOException e) {
            Log.e("BT", "Failed to connect", e);
        }
    }

    public void setCallbacks(Callback callback) {
        this.callback = callback;
    }

    private void startReading() {
        isReading = true;
        readThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (isReading) {
                try {
                    if (inputStream != null && (bytes = inputStream.read(buffer)) > 0) {
                        byte[] data = new byte[bytes];
                        System.arraycopy(buffer, 0, data, 0, bytes);
                        if (callback != null) callback.onReceive(data);
                    }
                } catch (IOException e) {
                    Log.e("BT", "Read error", e);
                    break;
                }
            }
        });
        readThread.start();
    }

    public void send(String message) {
        try {
            if (outputStream != null) {
                outputStream.write(message.getBytes());
            }
        } catch (IOException e) {
            Log.e("BT", "Send error", e);
        }
    }

    public void disconnect() {
        isReading = false;
        try { if (inputStream != null) inputStream.close(); } catch (IOException ignored) {}
        try { if (outputStream != null) outputStream.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
}
