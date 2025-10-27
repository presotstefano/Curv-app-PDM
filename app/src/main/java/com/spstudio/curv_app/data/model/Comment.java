package com.spstudio.curv_app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;

public class Comment {
    private final String id; // ID del documento commento
    private final String authorUid;
    @Nullable private final String authorName;
    @Nullable private final String authorAvatarUrl;
    private final String text;
    @Nullable private final Timestamp timestamp;

    private Comment(String id, String authorUid, @Nullable String authorName, @Nullable String authorAvatarUrl, String text, @Nullable Timestamp timestamp) {
        this.id = id;
        this.authorUid = authorUid;
        this.authorName = authorName;
        this.authorAvatarUrl = authorAvatarUrl;
        this.text = text;
        this.timestamp = timestamp;
    }

    @NonNull
    public static Comment fromFirestore(@NonNull DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) {
            return new Comment(doc.getId(), "", "Error", null, "", null);
        }

        return new Comment(
                doc.getId(),
                (String) data.getOrDefault("authorUid", ""),
                (String) data.get("authorName"),
                (String) data.get("authorAvatarUrl"),
                (String) data.getOrDefault("text", ""),
                (Timestamp) data.get("timestamp")
        );
    }

    // Getters
    public String getId() { return id; }
    public String getAuthorUid() { return authorUid; }
    @NonNull public String getAuthorName() { return authorName != null ? authorName : "A User"; }
    @Nullable public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    public String getText() { return text; }
    @Nullable public Timestamp getTimestamp() { return timestamp; }
}