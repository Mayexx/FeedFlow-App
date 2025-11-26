package com.example.feedflow;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class BluetoothSetUpActivity extends AppCompatActivity {

    private Button buttonSetup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetoothsetup); // Make sure this matches your XML filename

        buttonSetup = findViewById(R.id.buttonSetup);

        buttonSetup.setOnClickListener(v -> {
            Toast.makeText(this, "", Toast.LENGTH_SHORT).show();

            // Start the ConnectDeviceActivity
            Intent intent = new Intent(BluetoothSetUpActivity.this, DeviceSetUpActivity.class);
            startActivity(intent);
        });
    }
}
