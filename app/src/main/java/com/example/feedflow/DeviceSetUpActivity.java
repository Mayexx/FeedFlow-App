package com.example.feedflow;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class DeviceSetUpActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private BluetoothSerial serialBT;
    private String connectedDeviceAddress;
    private String connectedDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devicesetup);

        Spinner deviceSpinner = findViewById(R.id.deviceSpinner);
        Button confirmButton = findViewById(R.id.confirmButton);

        serialBT = new BluetoothSerial(this);

        // 🔹 Dummy list for deviceSpinner (replace with scanned devices if you implement scanning)
        List<String> devices = new ArrayList<>();
        devices.add("Select Device");
        devices.add("ESP32_Device_1 - AA:BB:CC:DD:EE:FF"); // Example MAC
        devices.add("ESP32_Device_2 - 11:22:33:44:55:66");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, devices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);

        confirmButton.setOnClickListener(v -> {
            String selected = (String) deviceSpinner.getSelectedItem();

            if (selected == null || selected.equals("Select Device")) {
                Toast.makeText(this, "Please select a device", Toast.LENGTH_SHORT).show();
                return;
            }

            // Extract MAC address from spinner string
            if (selected.contains("-")) {
                String[] parts = selected.split("-");
                connectedDeviceName = parts[0].trim();
                connectedDeviceAddress = parts[1].trim();
            } else {
                Toast.makeText(this, "Invalid device format", Toast.LENGTH_SHORT).show();
                return;
            }

            // 🔹 Check Bluetooth permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }

            // 🔹 Connect to ESP32 in a background thread
            new Thread(() -> {
                try {
                    serialBT.connect(connectedDeviceAddress); // blocking call

                    // Success → navigate to HomeActivity on UI thread
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(DeviceSetUpActivity.this, HomeActivity.class);
                        startActivity(intent);
                        finish();
                    });

                } catch (Exception e) {
                    Log.e("BT_CONNECT", "Connection failed", e);
                    runOnUiThread(() ->
                            Toast.makeText(DeviceSetUpActivity.this, "Bluetooth connection failed", Toast.LENGTH_SHORT).show()
                    );
                }
            }).start();
        });
    }

    // 🔹 Handle permission request
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted. Press confirm again.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
