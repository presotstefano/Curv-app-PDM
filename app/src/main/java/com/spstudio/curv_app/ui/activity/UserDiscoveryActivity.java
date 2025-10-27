package com.spstudio.curv_app.ui.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.UserModel;
import com.spstudio.curv_app.databinding.ActivityUserDiscoveryBinding;
import com.spstudio.curv_app.ui.adapter.UserAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserDiscoveryActivity extends AppCompatActivity implements UserAdapter.OnFollowToggleListener {

    private static final String TAG = "UserDiscoveryActivity";

    private ActivityUserDiscoveryBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private String currentUserId;

    private UserAdapter adapter;
    private final List<UserModel> userList = new ArrayList<>();
    // Set to store the UIDs of users the current user is following
    private final Set<String> followingIds = new HashSet<>();
    // Listener to keep the 'followingIds' set up-to-date in real-time
    private ListenerRegistration followingListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserDiscoveryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "User not logged in!");
            finish(); // Close activity if user is somehow not logged in
            return;
        }
        currentUserId = currentUser.getUid();

        // Setup Toolbar with back button
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupRecyclerView();
        listenToFollowingUpdates(); // Start listening for changes in the following list
        loadUsers(); // Load the initial list of users
    }

    private void setupRecyclerView() {
        // Pass the activity context, the up-to-date followingIds set, and the listener (this activity)
        adapter = new UserAdapter(this, followingIds, this);
        binding.usersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.usersRecyclerView.setAdapter(adapter);
    }

    // Listens for real-time changes to the current user's 'following' subcollection
    private void listenToFollowingUpdates() {
        if (currentUserId == null) return;

        followingListener = db.collection("utenti").document(currentUserId).collection("following")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Listen to following failed.", error);
                        // Optionally show an error, but don't clear the list on temporary errors
                        return;
                    }

                    Set<String> updatedFollowingIds = new HashSet<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            updatedFollowingIds.add(doc.getId()); // Add the UID of the followed user
                        }
                    }
                    // Update the local set *atomically* to avoid partial states
                    followingIds.clear();
                    followingIds.addAll(updatedFollowingIds);

                    Log.d(TAG, "Following list updated via listener. Current count: " + followingIds.size());

                    // Update the adapter with the full user list and the *latest* following set
                    // This ensures buttons reflect the real-time follow status
                    adapter.updateData(userList); // Pass the updated set
                    updateEmptyStateVisibility(); // Re-check empty state visibility
                });
    }


    // Loads the list of all users from Firestore, excluding the current user
    private void loadUsers() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.usersRecyclerView.setVisibility(View.GONE);
        binding.emptyTextView.setVisibility(View.GONE);

        db.collection("utenti")
                .orderBy("nomeUtente", Query.Direction.ASCENDING) // Order alphabetically
                // .limit(50) // Consider adding a limit for performance later
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (isDestroyed()) return; // Check if activity is still valid
                    binding.progressBar.setVisibility(View.GONE);
                    userList.clear(); // Clear the previous list

                    int totalDocs = querySnapshot.size();
                    int addedUsers = 0;
                    Log.d(TAG, " Firestore query returned " + totalDocs + " total user documents.");

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Log.d(TAG, "  Processing doc ID: " + doc.getId());
                        // Skip the currently logged-in user
                        if (!doc.getId().equals(currentUserId)) {
                            try {
                                userList.add(UserModel.fromFirestore(doc));
                                addedUsers++;
                                Log.d(TAG, "    -> Added user: " + doc.getString("nomeUtente"));
                            } catch (Exception e) {
                                Log.e(TAG, "    -> ERROR parsing user document " + doc.getId(), e);
                            }
                        } else {
                            Log.d(TAG, "    -> Skipping current user (ID: " + doc.getId() + ")");
                        }
                    }
                    Log.d(TAG, "Finished processing. Added " + addedUsers + " users to the list.");
                    // Update the adapter with the new user list and the current following set
                    adapter.updateData(userList);
                    updateEmptyStateVisibility(); // Call the method to show/hide views
                })
                .addOnFailureListener(e -> {
                    if (isDestroyed()) return;
                    Log.e(TAG, "Error loading users", e);
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(UserDiscoveryActivity.this, "Error loading users.", Toast.LENGTH_SHORT).show();
                    updateEmptyStateVisibility(); // Show empty state on error too
                });
    }

    // === METODO MANCANTE AGGIUNTO QUI ===
    // Shows or hides the "No other pilots found" text vs. the RecyclerView
    private void updateEmptyStateVisibility() {
        Log.d(TAG, "updateEmptyStateVisibility called. userList size: " + userList.size());
        if (userList.isEmpty()) {
            binding.usersRecyclerView.setVisibility(View.GONE);
            binding.emptyTextView.setVisibility(View.VISIBLE);
            Log.d(TAG, "  -> Showing empty text view.");
        } else {
            binding.usersRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyTextView.setVisibility(View.GONE);
            Log.d(TAG, "  -> Showing recycler view.");
        }
    }
    // === FINE METODO AGGIUNTO ===

    // --- Implementation of UserAdapter.OnFollowToggleListener ---

    @Override
    public void onFollow(UserModel userToFollow) {
        Log.d(TAG, "Follow action triggered for user: " + userToFollow.getUid());
        if (currentUserId == null) return;

        WriteBatch batch = db.batch();

        // Add followed user's ID to current user's 'following' subcollection
        DocumentReference followingRef = db.collection("utenti").document(currentUserId)
                .collection("following").document(userToFollow.getUid());
        batch.set(followingRef, new HashMap<>());

        // 2. Aggiungi a 'followers' dell'utente seguito
        DocumentReference followerRef = db.collection("utenti").document(userToFollow.getUid())
                .collection("followers").document(currentUserId);
        batch.set(followerRef, new HashMap<>());

        // Commit the batch
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully followed user: " + userToFollow.getUid());
                    // UI will update automatically via the followingListener
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error following user: " + userToFollow.getUid(), e);
                    Toast.makeText(this, getString(R.string.user_discovery_follow_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                    // Manually trigger adapter update to reset button state on failure
                    adapter.updateData(userList);
                });
    }

    @Override
    public void onUnfollow(UserModel userToUnfollow) {
        Log.d(TAG, "Unfollow action triggered for user: " + userToUnfollow.getUid());
        if (currentUserId == null) return;

        WriteBatch batch = db.batch();

        // Delete followed user's ID from current user's 'following' subcollection
        DocumentReference followingRef = db.collection("utenti").document(currentUserId)
                .collection("following").document(userToUnfollow.getUid());
        batch.delete(followingRef);

        // 2. Rimuovi da 'followers' dell'utente seguito
        DocumentReference followerRef = db.collection("utenti").document(userToUnfollow.getUid())
                .collection("followers").document(currentUserId);
        batch.delete(followerRef);

        // Commit the batch
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully unfollowed user: " + userToUnfollow.getUid());
                    // UI will update automatically via the followingListener
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error unfollowing user: " + userToUnfollow.getUid(), e);
                    Toast.makeText(this, getString(R.string.user_discovery_unfollow_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                    // Manually trigger adapter update to reset button state on failure
                    adapter.updateData(userList);
                });
    }

    // Cleanup the Firestore listener when the Activity is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (followingListener != null) {
            followingListener.remove(); // Stop listening to prevent memory leaks
        }
        binding = null; // Clean up ViewBinding
    }

    // Handle clicks on the Toolbar's back button
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Close this activity and return to the previous one
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}