package com.example.feedflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class HomeActivity extends AppCompatActivity {

    private TextView txtTemperature, txtFeedLevel, txtDeviceName, txtWaterTempStatus, txtFeedLevelStatus;
    private ProgressBar progressTemperature;
    private Button btnFeedNow, btnIncrease, btnDecrease;

    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private String connectedDeviceName = "Not Connected";
    private String ESP32_MAC = "B4:88:08:B4:03:6A"; // Replace with your ESP32 MAC
    private int feedAmount = 1;

    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize UI
        txtTemperature = findViewById(R.id.txtTemperature);
        txtFeedLevel = findViewById(R.id.txtFeedLevel);
        txtDeviceName = findViewById(R.id.txtDeviceName);
        txtWaterTempStatus = findViewById(R.id.txtWaterTempStatus);
        txtFeedLevelStatus = findViewById(R.id.txtFeedLevelStatus);
        progressTemperature = findViewById(R.id.progressTemperature);
        btnFeedNow = findViewById(R.id.btnFeedNow);
        btnIncrease = findViewById(R.id.btnIncrease);
        btnDecrease = findViewById(R.id.btnDecrease);

        // Setup feed buttons
        btnIncrease.setOnClickListener(v -> {
            if (feedAmount < 10) feedAmount++;
        });
        btnDecrease.setOnClickListener(v -> {
            if (feedAmount > 1) feedAmount--;
        });
        btnFeedNow.setOnClickListener(v -> sendBluetoothCommand("FEED:" + feedAmount));

        // Initialize Bluetooth
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            return;
        }
        if (!btAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show();
            return;
        }

        // Permissions check for Android 12+
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
            return;
        }

        // Connect to ESP32
        BluetoothDevice device = btAdapter.getRemoteDevice(ESP32_MAC);
        connectToDevice(device);
    }

    private void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                btSocket = device.createRfcommSocketToServiceRecord(BT_UUID);
                btSocket.connect();
                inputStream = btSocket.getInputStream();
                outputStream = btSocket.getOutputStream();
                connectedDeviceName = device.getName();

                runOnUiThread(() -> txtDeviceName.setText("Connected to: " + connectedDeviceName));

                listenForData();

            } catch (IOException e) {
                runOnUiThread(() -> txtDeviceName.setText("Connection failed"));
                e.printStackTrace();
            }
        }).start();
    }

    private void listenForData() {
        Handler handler = new Handler(getMainLooper());
        byte[] buffer = new byte[1024];

        new Thread(() -> {
            while (true) {
                try {
                    if (inputStream != null && inputStream.available() > 0) {
                        int bytes = inputStream.read(buffer);
                        String data = new String(buffer, 0, bytes).trim();

                        // Expecting format: "29.5:75" => temperature:feedLevel
                        if (data.contains(":")) {
                            String[] parts = data.split(":");
                            String tempStr = parts[0];
                            String feedStr = parts[1];

                            handler.post(() -> updateUI(tempStr, feedStr));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }

    @SuppressLint("SetTextI18n")
    private void updateUI(String tempStr, String feedStr) {
        try {
            double temp = Double.parseDouble(tempStr);
            double feedLevel = Double.parseDouble(feedStr);

            txtTemperature.setText(temp + " °C");
            progressTemperature.setProgress((int) temp);
            txtFeedLevel.setText(feedLevel + " %");

            // Temperature status
            if (temp >= 26 && temp <= 30) {
                txtWaterTempStatus.setText("Optimal");
                txtWaterTempStatus.setTextColor(Color.GREEN);
            } else if (temp > 30) {
                txtWaterTempStatus.setText("Too Hot");
                txtWaterTempStatus.setTextColor(Color.RED);
            } else {
                txtWaterTempStatus.setText("Too Cold");
                txtWaterTempStatus.setTextColor(Color.BLUE);
            }

            // Feed status
            if (feedLevel >= 5) {
                txtFeedLevelStatus.setText("Sufficient");
                txtFeedLevelStatus.setTextColor(Color.GREEN);
            } else if (feedLevel >= 2) {
                txtFeedLevelStatus.setText("Refill Soon");
                txtFeedLevelStatus.setTextColor(Color.YELLOW);
            } else {
                txtFeedLevelStatus.setText("Refill Now");
                txtFeedLevelStatus.setTextColor(Color.RED);
            }

        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private void sendBluetoothCommand(String command) {
        if (btSocket != null && btSocket.isConnected()) {
            try {
                outputStream.write((command + "\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Bluetooth not connected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (btSocket != null) btSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
