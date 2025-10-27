package com.spstudio.curv_app.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast; // Placeholder

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.Comment;
import com.spstudio.curv_app.ui.activity.UserProfileActivity;
// import com.spstudio.curv_app.ui.activity.UserProfileActivity; // Da creare

import java.text.SimpleDateFormat; // Per formattare timestamp (alternativa a logica custom)
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<Comment> comments;
    private final Context context;

    public CommentAdapter(Context context, List<Comment> comments) {
        this.context = context;
        this.comments = (comments != null) ? comments : new ArrayList<>();
    }

    public void updateData(List<Comment> newComments) {
        this.comments.clear();
        if (newComments != null) {
            this.comments.addAll(newComments);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        holder.bind(comment, context);

        // Click sull'intera riga per andare al profilo autore
        holder.itemView.setOnClickListener(v -> {
            String userId = comment.getAuthorUid();
            if (userId != null && !userId.isEmpty()) {
                Intent intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra("USER_ID", userId);
                context.startActivity(intent);
                //Toast.makeText(context, "Go to profile: " + comment.getAuthorName(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        ImageView authorAvatarImageView;
        TextView authorNameTextView;
        TextView timestampTextView;
        TextView commentTextTextView;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            authorAvatarImageView = itemView.findViewById(R.id.authorAvatarImageView);
            authorNameTextView = itemView.findViewById(R.id.authorNameTextView);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
            commentTextTextView = itemView.findViewById(R.id.commentTextTextView);
        }

        public void bind(Comment comment, Context context) {
            authorNameTextView.setText(comment.getAuthorName());
            commentTextTextView.setText(comment.getText());
            timestampTextView.setText(formatTimestamp(comment.getTimestamp(), context));

            // Carica immagine profilo con Glide
            Glide.with(context)
                    .load(comment.getAuthorAvatarUrl())
                    .placeholder(R.drawable.ic_person) // Placeholder
                    .error(R.drawable.ic_person)       // Errore
                    .circleCrop()
                    .into(authorAvatarImageView);
        }

        // Funzione helper per formattare il timestamp (simile a quella usata in Flutter)
        private String formatTimestamp(Timestamp timestamp, Context context) {
            if (timestamp == null) return "";
            long diff = System.currentTimeMillis() - timestamp.toDate().getTime();
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            long days = TimeUnit.MILLISECONDS.toDays(diff);

            if (minutes < 1) return context.getString(R.string.time_suffix_now); // Aggiungi R.string.time_suffix_now
            if (minutes < 60) return minutes + context.getString(R.string.time_suffix_minute); // Aggiungi R.string.time_suffix_minute
            if (hours < 24) return hours + context.getString(R.string.time_suffix_hour); // Aggiungi R.string.time_suffix_hour
            if (days < 7) return days + context.getString(R.string.time_suffix_day); // Aggiungi R.string.time_suffix_day
            else return (days / 7) + context.getString(R.string.time_suffix_week); // Aggiungi R.string.time_suffix_week
        }
    }
}