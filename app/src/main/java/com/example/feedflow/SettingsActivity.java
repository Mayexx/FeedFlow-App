package com.example.feedflow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.IOException;

public class SettingsActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 100;
    private ShapeableImageView imgAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        imgAvatar = findViewById(R.id.imgAvatar);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        // Load saved avatar if any
        String uriStr = getSharedPreferences("settings_prefs", MODE_PRIVATE)
                .getString("avatarUri", null);
        if (uriStr != null) {
            imgAvatar.setImageURI(Uri.parse(uriStr));
        }

        // Tap avatar to select image
        imgAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE);
        });

        setupBottomNavigation(bottomNav);
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_settings);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                startActivity(new Intent(SettingsActivity.this, HomeActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_stats) {
                startActivity(new Intent(SettingsActivity.this, StatsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_notes) {
                startActivity(new Intent(SettingsActivity.this, NotesActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_alerts) {
                startActivity(new Intent(SettingsActivity.this, AlertsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_settings) {
                return true; // already here
            } else {
                return false;
            }
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                imgAvatar.setImageBitmap(bitmap);

                // Save Uri for persistence
                getSharedPreferences("settings_prefs", MODE_PRIVATE)
                        .edit().putString("avatarUri", imageUri.toString()).apply();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
