package com.spstudio.curv_app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.Map;

public class GroupModel {
    private final String id;
    private final String name;
    private final String description;
    @Nullable private final String imageUrl;
    private final String creatorUid;
    private final Timestamp createdAt;
    private final boolean isPublic;
    private final int memberCount;
    @Nullable private final String countryCode;

    public GroupModel(String id, String name, String description, @Nullable String imageUrl, String creatorUid, Timestamp createdAt, boolean isPublic, int memberCount, @Nullable String countryCode) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.creatorUid = creatorUid;
        this.createdAt = createdAt;
        this.isPublic = isPublic;
        this.memberCount = memberCount;
        this.countryCode = countryCode;
    }

    @NonNull
    public static GroupModel fromFirestore(DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) {
            return new GroupModel("", "Error", "", null, "", Timestamp.now(), false, 0, null);
        }
        return new GroupModel(
                doc.getId(),
                (String) data.getOrDefault("name", "Unnamed Group"),
                (String) data.getOrDefault("description", ""),
                (String) data.get("imageUrl"),
                (String) data.getOrDefault("creatorUid", ""),
                (Timestamp) data.getOrDefault("createdAt", Timestamp.now()),
                (Boolean) data.getOrDefault("isPublic", true),
                ((Number) data.getOrDefault("memberCount", 0)).intValue(),
                (String) data.get("countryCode")
        );
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    // ... (aggiungi altri getter se necessari)
}