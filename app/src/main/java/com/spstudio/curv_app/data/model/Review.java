package com.spstudio.curv_app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;

public class Review {
    private final String id; // ID del documento recensione (che è l'UID dell'utente)
    private final String userId;
    @Nullable private final String userName;
    private final int rating;
    @Nullable private final String commento;
    @Nullable private final Timestamp timestamp;
    private final int replyCount; // Aggiunto per gestire le risposte

    private Review(String id, String userId, @Nullable String userName, int rating, @Nullable String commento, @Nullable Timestamp timestamp, int replyCount) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.rating = rating;
        this.commento = commento;
        this.timestamp = timestamp;
        this.replyCount = replyCount;
    }

    @NonNull
    public static Review fromFirestore(@NonNull DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) {
            return new Review(doc.getId(), "", "Error", 0, null, null, 0);
        }

        return new Review(
                doc.getId(), // L'ID del documento è l'UID dell'utente che ha lasciato la recensione
                (String) data.getOrDefault("userId", ""), // Anche se ridondante, teniamolo per coerenza
                (String) data.get("userName"),
                ((Number) data.getOrDefault("rating", 0)).intValue(),
                (String) data.get("commento"),
                (Timestamp) data.get("timestamp"),
                ((Number) data.getOrDefault("replyCount", 0)).intValue() // Legge il contatore delle risposte
        );
    }

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    @NonNull public String getUserName() { return userName != null ? userName : "Anonymous"; }
    public int getRating() { return rating; }
    @Nullable public String getCommento() { return commento; }
    @Nullable public Timestamp getTimestamp() { return timestamp; }
    public int getReplyCount() { return replyCount; }
}