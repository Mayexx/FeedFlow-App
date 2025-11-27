package com.example.feedflow;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.CollectionReference;
import java.util.List;
import java.util.Map;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    private final List<Map<String, Object>> notesList;
    private final Context context;
    private final CollectionReference notesRef;

    public NotesAdapter(Context context, List<Map<String, Object>> notesList, CollectionReference notesRef) {
        this.context = context;
        this.notesList = notesList;
        this.notesRef = notesRef;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Map<String, Object> noteData = notesList.get(position);

        // Build display string
        String displayText = "Dead Fish: " + safeGet(noteData, "deadFish") +
                ", Temp: " + safeGet(noteData, "temperature") + "°C" +
                ", Weather: " + safeGet(noteData, "weather") +
                ", Feeding: " + safeGet(noteData, "feedingTime") +
                ", Amount: " + safeGet(noteData, "amount") + "kg" +
                ", Behaviour: " + safeGet(noteData, "behaviour") +
                (noteData.get("notes") == null ? "" : ", Notes: " + noteData.get("notes"));

        holder.tvNoteSummary.setText(displayText);

        // Open detail on click
        holder.itemView.setOnClickListener(v -> {
            String noteId = (String) noteData.get("id");
            if (noteId != null) {
                Intent intent = new Intent(context, NoteDetailActivity.class);
                intent.putExtra("note_id", noteId);
                context.startActivity(intent);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            String docId = (String) noteData.get("id");
            if (docId != null) {
                new AlertDialog.Builder(context)
                        .setTitle("Delete Note")
                        .setMessage("Are you sure you want to delete this note?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // Capture the adapter position safely
                            int adapterPosition = holder.getAdapterPosition();
                            if (adapterPosition == RecyclerView.NO_POSITION) return;

                            notesRef.document(docId).delete()
                                    .addOnSuccessListener(aVoid -> {
                                        if (adapterPosition >= 0 && adapterPosition < notesList.size()) {
                                            notesList.remove(adapterPosition);
                                            notifyItemRemoved(adapterPosition);
                                            notifyItemRangeChanged(adapterPosition, notesList.size());
                                            Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(context, "Failed to delete note", Toast.LENGTH_SHORT).show();
                                    });
                        })
                        .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return notesList.size();
    }

    // Null-safe getter
    private String safeGet(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : "-";
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvNoteSummary;
        Button btnDelete;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNoteSummary = itemView.findViewById(R.id.tvNoteSummary);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
