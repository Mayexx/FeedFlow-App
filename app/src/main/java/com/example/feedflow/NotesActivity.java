package com.example.feedflow;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NotesActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "FeedFlowNotes";
    private static final String NOTES_KEY = "saved_notes";

    private EditText editTextDeadFish, editTextTemp, editTextAmount, editTextNotes;
    private Spinner spinnerWeather, spinnerFeedingTime, spinnerBehaviour;
    private Button btnRecord;
    private RecyclerView recyclerViewNotes;

    private NotesAdapter notesAdapter;
    private List<String> notesList;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);


        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        // Init SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load saved notes from JSON
        notesList = loadNotes();

        // Init UI components
        editTextDeadFish = findViewById(R.id.editTextDeadFish);
        editTextTemp = findViewById(R.id.editTextTemp);
        editTextAmount = findViewById(R.id.editTextAmount);
        editTextNotes = findViewById(R.id.editTextNotes);
        spinnerWeather = findViewById(R.id.spinnerWeather);
        spinnerFeedingTime = findViewById(R.id.spinnerFeedingTime);
        spinnerBehaviour = findViewById(R.id.spinnerBehaviour);
        btnRecord = findViewById(R.id.btnRecord);
        recyclerViewNotes = findViewById(R.id.recyclerViewNotes);

        // Setup Spinners
        setupSpinners();

        // Setup RecyclerView
        notesAdapter = new NotesAdapter(this, notesList);
        recyclerViewNotes.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewNotes.setAdapter(notesAdapter);

        // Handle Record button
        btnRecord.setOnClickListener(v -> saveNote());

        setupBottomNavigation(bottomNav);
    }

    private void setupSpinners() {
        // Weather
        ArrayAdapter<CharSequence> weatherAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.weather_array,
                android.R.layout.simple_spinner_dropdown_item
        );
        spinnerWeather.setAdapter(weatherAdapter);

        // Feeding time
        ArrayAdapter<CharSequence> feedingAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.feeding_array,
                android.R.layout.simple_spinner_dropdown_item
        );
        spinnerFeedingTime.setAdapter(feedingAdapter);

        // Behaviour
        ArrayAdapter<CharSequence> behaviourAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.behaviour_array,
                android.R.layout.simple_spinner_dropdown_item
        );
        spinnerBehaviour.setAdapter(behaviourAdapter);
    }

    private void saveNote() {
        String deadFish = editTextDeadFish.getText().toString().trim();
        String temp = editTextTemp.getText().toString().trim();
        String amount = editTextAmount.getText().toString().trim();
        String notes = editTextNotes.getText().toString().trim();
        String weather = spinnerWeather.getSelectedItem().toString();
        String feedingTime = spinnerFeedingTime.getSelectedItem().toString();
        String behaviour = spinnerBehaviour.getSelectedItem().toString();

        // Build note string
        String record = "Dead Fish: " + deadFish +
                ", Temp: " + temp + "°C" +
                ", Weather: " + weather +
                ", Feeding: " + feedingTime +
                ", Amount: " + amount + "kg" +
                ", Behaviour: " + behaviour +
                (notes.isEmpty() ? "" : ", Notes: " + notes);

        // Add to list + adapter
        notesList.add(record);
        notesAdapter.notifyItemInserted(notesList.size() - 1);

        // Save list as JSON string
        saveNotes(notesList);

        // Clear inputs
        editTextDeadFish.setText("");
        editTextTemp.setText("");
        editTextAmount.setText("");
        editTextNotes.setText("");
        spinnerWeather.setSelection(0);
        spinnerFeedingTime.setSelection(0);
        spinnerBehaviour.setSelection(0);
    }

    private void saveNotes(List<String> list) {
        JSONArray jsonArray = new JSONArray(list);
        sharedPreferences.edit().putString(NOTES_KEY, jsonArray.toString()).apply();
    }

    private List<String> loadNotes() {
        List<String> list = new ArrayList<>();
        try {
            String json = sharedPreferences.getString(NOTES_KEY, null);
            if (json != null && !json.isEmpty()) {
                JSONArray jsonArray = new JSONArray(json);
                for (int i = 0; i < jsonArray.length(); i++) {
                    list.add(jsonArray.getString(i));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_notes);

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
            } else if (id == R.id.nav_notes) {
                return true;
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
