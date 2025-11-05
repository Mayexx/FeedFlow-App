package com.example.feedflow;

import android.content.Intent;
import android.content.SharedPreferences;
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

import java.util.ArrayList;
import java.util.List;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;


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

    private DatabaseReference notesRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        // Reference to "notes" node in Firebase
        notesRef = FirebaseDatabase.getInstance().getReference("notes");

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

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
        notesList = new ArrayList<>();
        notesAdapter = new NotesAdapter(this, notesList);
        recyclerViewNotes.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewNotes.setAdapter(notesAdapter);

        // Load notes from Firebase
        loadNotesFromFirebase();

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

        // Save to Firebase as string
        String noteId = notesRef.push().getKey(); // generate unique ID
        if (noteId != null) {
            notesRef.child(noteId).setValue(record);
        }

        Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show();

        // Clear inputs
        editTextDeadFish.setText("");
        editTextTemp.setText("");
        editTextAmount.setText("");
        editTextNotes.setText("");
        spinnerWeather.setSelection(0);
        spinnerFeedingTime.setSelection(0);
        spinnerBehaviour.setSelection(0);
    }

    private void loadNotesFromFirebase() {
        notesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                notesList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String note = snapshot.getValue(String.class);
                    notesList.add(note);
                }
                notesAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(NotesActivity.this, "Failed to load notes", Toast.LENGTH_SHORT).show();
            }
        });
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
            }
            return false;
        });
    }
}
