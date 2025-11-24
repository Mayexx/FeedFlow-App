package com.example.feedflow;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.CollectionReference;

import java.util.List;
import java.util.Map;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    private List<Map<String, Object>> notesList;
    private Context context;
    private CollectionReference notesRef;

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

        String displayText = "Dead Fish: " + noteData.get("deadFish") +
                ", Temp: " + noteData.get("temperature") + "°C" +
                ", Weather: " + noteData.get("weather") +
                ", Feeding: " + noteData.get("feedingTime") +
                ", Amount: " + noteData.get("amount") + "kg" +
                ", Behaviour: " + noteData.get("behaviour") +
                (noteData.get("notes") == null ? "" : ", Notes: " + noteData.get("notes"));

        holder.tvNote.setText(displayText);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, NoteDetailActivity.class);
            intent.putExtra("note_id", (String) noteData.get("id"));
            context.startActivity(intent);
        });

        holder.btnDelete.setOnClickListener(v -> {
            String docId = (String) noteData.get("id");
            if (docId != null) {
                notesRef.document(docId).delete()
                        .addOnSuccessListener(aVoid -> notesList.remove(position))
                        .addOnFailureListener(e -> {});
                notifyItemRemoved(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notesList.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvNote;
        Button btnDelete;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNote = itemView.findViewById(R.id.tvNote);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
