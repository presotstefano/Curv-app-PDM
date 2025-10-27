package com.spstudio.curv_app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.Map;

public class UserModel {
    private final String uid;
    @Nullable private final String nomeUtente;
    @Nullable private final String bio;
    @Nullable private final String profileImageUrl;
    // Add counts if needed later
    // private final int followerCount;
    // private final int followingCount;

    // UI state flag (not in Firestore)
    private boolean isFollowedByCurrentUser = false;

    private UserModel(String uid, @Nullable String nomeUtente, @Nullable String bio, @Nullable String profileImageUrl) {
        this.uid = uid;
        this.nomeUtente = nomeUtente;
        this.bio = bio;
        this.profileImageUrl = profileImageUrl;
    }

    @NonNull
    public static UserModel fromFirestore(@NonNull DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) {
            return new UserModel(doc.getId(), "Error", null, null);
        }
        return new UserModel(
                doc.getId(),
                (String) data.get("nomeUtente"),
                (String) data.get("bio"),
                (String) data.get("profileImageUrl")
                // Add counts here if reading them
        );
    }

    // Getters
    public String getUid() { return uid; }
    @Nullable public String getNomeUtente() { return nomeUtente; }
    @Nullable public String getBio() { return bio; }
    @Nullable public String getProfileImageUrl() { return profileImageUrl; }

    // Follow state getter/setter
    public boolean isFollowedByCurrentUser() { return isFollowedByCurrentUser; }
    public void setFollowedByCurrentUser(boolean followed) { isFollowedByCurrentUser = followed; }
}