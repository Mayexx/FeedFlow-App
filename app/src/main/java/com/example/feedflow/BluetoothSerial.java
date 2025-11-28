package com.example.feedflow;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothSerial {

    private final Context context;
    private final BluetoothAdapter btAdapter;
    private BluetoothDevice btDevice;
    private BluetoothSocket btSocket;

    private OutputStream outputStream;
    private InputStream inputStream;

    private Thread readThread;
    private boolean reading = false;

    private static final UUID BT_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private DataCallback callback;

    public BluetoothSerial(Context context) {
        this.context = context;
        this.btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void setCallbacks(DataCallback callback) {
        this.callback = callback;
    }

    public interface DataCallback {
        void onDataReceived(String data);
        void onConnectionFailed(Exception e);
        void onDisconnected();
    }

    public void connect(String macAddress) {
        new Thread(() -> {
            try {
                btDevice = btAdapter.getRemoteDevice(macAddress);

                if (ActivityCompat.checkSelfPermission(context,
                        android.Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e("BT", "Missing BLUETOOTH_CONNECT permission");
                    return;
                }

                btSocket = btDevice.createRfcommSocketToServiceRecord(BT_UUID);
                btAdapter.cancelDiscovery();
                btSocket.connect();

                outputStream = btSocket.getOutputStream();
                inputStream = btSocket.getInputStream();

                startReading();

            } catch (Exception e) {
                Log.e("BT_SERIAL", "Connection failed", e);
                if (callback != null) callback.onConnectionFailed(e);
            }
        }).start();
    }

    private void startReading() {
        reading = true;
        readThread = new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[1024];

            try {
                while (reading && inputStream != null) {
                    int bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String part = new String(buffer, 0, bytes);
                        sb.append(part);

                        int index;
                        while ((index = sb.indexOf("\n")) != -1) {
                            String line = sb.substring(0, index).trim();
                            sb.delete(0, index + 1);

                            if (callback != null) callback.onDataReceived(line);
                        }
                    }
                }

            } catch (Exception e) {
                Log.e("BT_READ", "Read error", e);

            } finally {
                disconnect();
            }
        });

        readThread.start();
    }

    public void send(String msg) {
        try {
            if (outputStream != null) {
                outputStream.write(msg.getBytes());
                outputStream.flush();
            }
        } catch (Exception e) {
            Log.e("BT_SEND", "Send error", e);
        }
    }

    public void disconnect() {
        reading = false;

        try {
            if (btSocket != null) {
                btSocket.close();
            }
        } catch (Exception ignored) {}

        if (callback != null) callback.onDisconnected();
    }

    public boolean isConnected() {
        return btSocket != null && btSocket.isConnected();
    }
}
