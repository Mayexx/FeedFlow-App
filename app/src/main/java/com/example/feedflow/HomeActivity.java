package com.example.feedflow;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private BluetoothSerial serialBT;

    private TextView waterTemperature, txtFeedLevel, txtDeviceName;
    private ProgressBar progressTemperature;

    private FirebaseFirestore db;

    private String connectedDeviceAddress = "XX:XX:XX:XX:XX:XX"; // Replace with your ESP32 MAC
    private String connectedDeviceName = "ESP32";

    private final String CHANNEL_ID = "alerts_channel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize views
        waterTemperature = findViewById(R.id.waterTemperature);
        txtFeedLevel = findViewById(R.id.txtFeedLevel);
        txtDeviceName = findViewById(R.id.txtDeviceName);
        progressTemperature = findViewById(R.id.progressTemperature);

        // Firebase
        db = FirebaseFirestore.getInstance();

        // Bluetooth
        serialBT = new BluetoothSerial(this);  // pass the activity context
        serialBT.connect(connectedDeviceAddress); // your MAC address
        serialBT.setCallbacks(data -> {
            String received = new String(data).trim();
            runOnUiThread(() -> parseAndUpdateUI(received));
        });

        // Notification channel
        createNotificationChannel();

        setupBluetoothListener();
    }

    private void setupBluetoothListener() {
        serialBT.setCallbacks(data -> {
            String received = new String(data).trim();
            runOnUiThread(() -> parseAndUpdateUI(received));
        });
    }

    private void parseAndUpdateUI(String received) {
        try {
            String tempStr = "", feedStr = "", time = "";

            String[] parts = received.split(",");
            for (String part : parts) {
                if (part.startsWith("temp:")) tempStr = part.split(":")[1];
                else if (part.startsWith("feed:")) feedStr = part.split(":")[1];
                else if (part.startsWith("time:")) time = part.split(":")[1];
            }

            updateUI(tempStr, feedStr, time);

        } catch (Exception e) {
            Log.e("BT_DATA", "Failed to parse: " + received, e);
        }
    }

    private void updateUI(String tempStr, String feedStr, String time) {
        double temp = 0;
        double feedLevel = 0;

        try {
            temp = Double.parseDouble(tempStr);
            feedLevel = Double.parseDouble(feedStr);
        } catch (NumberFormatException e) {
            Log.e("BT_DATA", "Invalid data: " + tempStr + ", " + feedStr);
            return;
        }

        // Update UI
        waterTemperature.setText(String.format("%.1f °C", temp));
        progressTemperature.setProgress((int) temp);
        txtFeedLevel.setText(String.format("%.1f%%", feedLevel));
        txtDeviceName.setText("Connected to: " + connectedDeviceName + "\nLast Updated: " + time);

        // Send alerts if thresholds exceeded
        if (temp > 30) sendLocalNotification("Temperature Alert", "Water temp high: " + temp + "°C");
        if (feedLevel < 20) sendLocalNotification("Feed Alert", "Feed level low: " + feedLevel + "%");

        // Save to Firestore
        Map<String, Object> sensorData = new HashMap<>();
        sensorData.put("temperature", temp);
        sensorData.put("feedLevel", feedLevel);
        sensorData.put("timestamp", System.currentTimeMillis());

        db.collection("FeedFlow").document("Device001")
                .collection("readings").add(sensorData)
                .addOnSuccessListener(docRef -> Log.d("FIRESTORE", "Data added"))
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to add", e));

        // Update latest values
        Map<String, Object> latestData = new HashMap<>();
        latestData.put("temperature", temp);
        latestData.put("feedLevel", feedLevel);
        latestData.put("lastUpdated", System.currentTimeMillis());

        db.collection("FeedFlow").document("Device001")
                .set(latestData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d("FIRESTORE", "Latest values updated"))
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to update", e));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for temperature and feed alerts");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void sendLocalNotification(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alerts)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (manager != null) manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serialBT != null) serialBT.disconnect();
    }
}
