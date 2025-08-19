package com.example.feedflow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotesActivity extends AppCompatActivity {

    private EditText editTextDeadFish, editTextTemp, editTextAmount, editTextNotes;
    private Spinner spinnerWeather, spinnerFeedingTime, spinnerFeedType, spinnerBehaviour;
    private LinearLayout savedNotesContainer;
    private SharedPreferences sharedPreferences;

    private static final String PREFS_NAME = "FeedFlowNotes";
    private static final String NOTES_KEY = "saved_notes";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Initialize UI
        editTextDeadFish = findViewById(R.id.editTextDeadFish);
        editTextTemp = findViewById(R.id.editTextTemp);
        editTextAmount = findViewById(R.id.editTextAmount);
        editTextNotes = findViewById(R.id.editTextNotes);

        spinnerWeather = findViewById(R.id.spinnerWeather);
        spinnerFeedingTime = findViewById(R.id.spinnerFeedingTime);
        spinnerFeedType = findViewById(R.id.spinnerFeedType);
        spinnerBehaviour = findViewById(R.id.spinnerBehaviour);

        savedNotesContainer = findViewById(R.id.savedNotesContainer); // ✅ FIX: initialize container

        Button btnRecord = findViewById(R.id.btnRecord);

        // Populate Spinners
        setupSpinners();

        // Load previously saved notes
        loadSavedNotes();

        // Save note when button clicked
        btnRecord.setOnClickListener(v -> saveNote());

        // Setup bottom navigation
        setupBottomNavigation(bottomNav);
    }

    private void setupSpinners() {
        ArrayAdapter<String> weatherAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Select", "Sunny", "Rainy", "Cloudy"});
        spinnerWeather.setAdapter(weatherAdapter);

        ArrayAdapter<String> feedingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Select", "Morning", "Afternoon", "Evening"});
        spinnerFeedingTime.setAdapter(feedingAdapter);

        ArrayAdapter<String> feedTypeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Select", "Pellets", "Flakes", "Live Food"});
        spinnerFeedType.setAdapter(feedTypeAdapter);

        ArrayAdapter<String> behaviourAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Select", "Normal", "Aggressive", "Lethargic"});
        spinnerBehaviour.setAdapter(behaviourAdapter);
    }

    private void saveNote() {
        String note = "Dead Fish: " + editTextDeadFish.getText().toString() +
                "\nTemp: " + editTextTemp.getText().toString() + "°C" +
                "\nWeather: " + spinnerWeather.getSelectedItem().toString() +
                "\nFeeding Time: " + spinnerFeedingTime.getSelectedItem().toString() +
                "\nFeed Type: " + spinnerFeedType.getSelectedItem().toString() +
                "\nAmount: " + editTextAmount.getText().toString() + " kg" +
                "\nBehaviour: " + spinnerBehaviour.getSelectedItem().toString() +
                "\nNotes: " + editTextNotes.getText().toString();

        Set<String> notesSet = sharedPreferences.getStringSet(NOTES_KEY, new HashSet<>());
        Set<String> updatedSet = new HashSet<>(notesSet);
        updatedSet.add(note);

        sharedPreferences.edit().putStringSet(NOTES_KEY, updatedSet).apply();

        // Refresh displayed notes
        loadSavedNotes();

        // Clear inputs
        editTextDeadFish.setText("");
        editTextTemp.setText("");
        editTextAmount.setText("");
        editTextNotes.setText("");
        spinnerWeather.setSelection(0);
        spinnerFeedingTime.setSelection(0);
        spinnerFeedType.setSelection(0);
        spinnerBehaviour.setSelection(0);
    }

    private void loadSavedNotes() {
        savedNotesContainer.removeAllViews();
        Set<String> notesSet = sharedPreferences.getStringSet(NOTES_KEY, new HashSet<>());
        List<String> notesList = new ArrayList<>(notesSet);

        for (String note : notesList) {
            TextView noteView = new TextView(this);
            noteView.setText(note);
            noteView.setPadding(16, 16, 16, 16);
            noteView.setBackgroundResource(android.R.drawable.editbox_background_normal);
            savedNotesContainer.addView(noteView);
        }
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_notes); // highlight Notes tab

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                startActivity(new Intent(NotesActivity.this, HomeActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_stats) {
                startActivity(new Intent(NotesActivity.this, StatsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_notes) {
                return true; // already in Notes
            } else if (id == R.id.nav_alerts) {
                startActivity(new Intent(NotesActivity.this, AlertsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(NotesActivity.this, SettingsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            }
            return false;
        });
    }



}
