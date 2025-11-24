package com.example.feedflow;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotesActivity extends AppCompatActivity {

    private EditText editTextDeadFish, editTextTemp, editTextAmount, editTextNotes;
    private Spinner spinnerWeather, spinnerFeedingTime, spinnerBehaviour;
    private Button btnRecord;
    private RecyclerView recyclerViewNotes;

    private NotesAdapter notesAdapter;
    private List<Map<String, Object>> notesList;

    private FirebaseFirestore db;
    private CollectionReference notesRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        db = FirebaseFirestore.getInstance();
        notesRef = db.collection("notes");

        // UI components
        editTextDeadFish = findViewById(R.id.editTextDeadFish);
        editTextTemp = findViewById(R.id.editTextTemp);
        editTextAmount = findViewById(R.id.editTextAmount);
        editTextNotes = findViewById(R.id.editTextNotes);
        spinnerWeather = findViewById(R.id.spinnerWeather);
        spinnerFeedingTime = findViewById(R.id.spinnerFeedingTime);
        spinnerBehaviour = findViewById(R.id.spinnerBehaviour);
        btnRecord = findViewById(R.id.btnRecord);
        recyclerViewNotes = findViewById(R.id.recyclerViewNotes);

        setupSpinners();

        // RecyclerView setup
        notesList = new ArrayList<>();
        notesAdapter = new NotesAdapter(this, notesList, notesRef);
        recyclerViewNotes.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewNotes.setAdapter(notesAdapter);

        // Load notes from Firestore
        loadNotesFromFirestore();

        btnRecord.setOnClickListener(v -> saveNote());

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        setupBottomNavigation(bottomNav);
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> weatherAdapter = ArrayAdapter.createFromResource(
                this, R.array.weather_array, android.R.layout.simple_spinner_dropdown_item);
        spinnerWeather.setAdapter(weatherAdapter);

        ArrayAdapter<CharSequence> feedingAdapter = ArrayAdapter.createFromResource(
                this, R.array.feeding_array, android.R.layout.simple_spinner_dropdown_item);
        spinnerFeedingTime.setAdapter(feedingAdapter);

        ArrayAdapter<CharSequence> behaviourAdapter = ArrayAdapter.createFromResource(
                this, R.array.behaviour_array, android.R.layout.simple_spinner_dropdown_item);
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

        Map<String, Object> noteMap = new HashMap<>();
        noteMap.put("deadFish", deadFish);
        noteMap.put("temperature", temp);
        noteMap.put("weather", weather);
        noteMap.put("feedingTime", feedingTime);
        noteMap.put("amount", amount);
        noteMap.put("behaviour", behaviour);
        noteMap.put("notes", notes);

        notesRef.add(noteMap)
                .addOnSuccessListener(docRef -> Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save note", Toast.LENGTH_SHORT).show());

        // Clear inputs
        editTextDeadFish.setText("");
        editTextTemp.setText("");
        editTextAmount.setText("");
        editTextNotes.setText("");
        spinnerWeather.setSelection(0);
        spinnerFeedingTime.setSelection(0);
        spinnerBehaviour.setSelection(0);
    }

    private void loadNotesFromFirestore() {
        notesRef.addSnapshotListener((querySnapshot, e) -> {
            if (e != null) {
                Toast.makeText(this, "Failed to load notes", Toast.LENGTH_SHORT).show();
                return;
            }
            notesList.clear();
            if (querySnapshot != null) {
                for (QueryDocumentSnapshot doc : querySnapshot) {
                    Map<String, Object> note = doc.getData();
                    note.put("id", doc.getId()); // include document ID for detail activity
                    notesList.add(note);
                }
                notesAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        bottomNav.setSelectedItemId(R.id.nav_notes);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                overridePendingTransition(0,0); finish();
                return true;
            } else if (id == R.id.nav_stats) {
                startActivity(new Intent(this, StatsActivity.class));
                overridePendingTransition(0,0); finish();
                return true;
            } else if (id == R.id.nav_notes) {
                return true;
            } else if (id == R.id.nav_alerts) {
                startActivity(new Intent(this, AlertsActivity.class));
                overridePendingTransition(0,0); finish();
                return true;
            }
            return false;
        });
    }
}
