package com.spstudio.curv_app.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.UserModel;
import com.spstudio.curv_app.ui.activity.UserProfileActivity;
// import com.spstudio.curv_app.ui.activity.UserProfileActivity; // Uncomment when created

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.hdodenhof.circleimageview.CircleImageView;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private final List<UserModel> users = new ArrayList<>();
    private final Set<String> followingIds; // Set of IDs the current user is following
    private final Context context;
    private final OnFollowToggleListener listener;

    // Interface to communicate follow/unfollow actions back to the Activity
    public interface OnFollowToggleListener {
        void onFollow(UserModel userToFollow);
        void onUnfollow(UserModel userToUnfollow);
    }

    public UserAdapter(Context context, Set<String> followingIds, OnFollowToggleListener listener) {
        this.context = context;
        this.followingIds = followingIds; // Keep a reference to the Activity's set
        this.listener = listener;
    }

    // Updates the adapter's data and triggers UI refresh
    public void updateData(List<UserModel> newUsers) {
        this.users.clear();
        if (newUsers != null) {
            this.users.addAll(newUsers);
        }
        // Update the follow state for each user based on the latest followingIds set
        for (UserModel user : this.users) {
            user.setFollowedByCurrentUser(this.followingIds.contains(user.getUid()));
        }
        notifyDataSetChanged(); // Refresh the RecyclerView
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        UserModel user = users.get(position);
        holder.bind(user, context, listener);

        // Click sull'intera riga per andare al profilo
        holder.itemView.setOnClickListener(v -> {
            // === SCOMMENTA E VERIFICA QUESTE RIGHE ===
            Intent intent = new Intent(context, UserProfileActivity.class);
            intent.putExtra("USER_ID", user.getUid()); // Passa l'ID dell'utente cliccato
            context.startActivity(intent);
            // Toast.makeText(context, "Go to profile: " + user.getNomeUtente(), Toast.LENGTH_SHORT).show(); // Rimuovi il Toast
            // === FINE MODIFICA ===
        });
    }

    @Override
    public int getItemCount() {
        // === AGGIUNGI QUESTO LOG ===
        Log.d("UserAdapter", "getItemCount() called, returning: " + users.size());
        // === FINE LOG AGGIUNTO ===
        return users.size();
    }
    // --- ViewHolder ---
    static class UserViewHolder extends RecyclerView.ViewHolder {
        CircleImageView avatarImageView;
        TextView usernameTextView;
        TextView bioTextView;
        MaterialButton followButton;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.userAvatarImageView);
            usernameTextView = itemView.findViewById(R.id.usernameTextView);
            bioTextView = itemView.findViewById(R.id.bioTextView);
            followButton = itemView.findViewById(R.id.followButton);
        }

        public void bind(UserModel user, Context context, OnFollowToggleListener listener) {
            usernameTextView.setText(user.getNomeUtente() != null ? user.getNomeUtente() : context.getString(R.string.user_profile_unnamed_pilot));
            bioTextView.setText(user.getBio() != null ? user.getBio() : "");
            bioTextView.setVisibility(user.getBio() != null && !user.getBio().isEmpty() ? View.VISIBLE : View.GONE);

            Glide.with(context)
                    .load(user.getProfileImageUrl())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(avatarImageView);

            // Update button appearance and set click listener
            updateFollowButtonState(user, context);
            followButton.setOnClickListener(v -> {
                followButton.setEnabled(false); // Temporarily disable button
                if (user.isFollowedByCurrentUser()) {
                    listener.onUnfollow(user);
                } else {
                    listener.onFollow(user);
                }
                // The button will be re-enabled and updated when the adapter's updateData is called
            });
        }

        // Updates the text, icon, and style of the follow button
        private void updateFollowButtonState(UserModel user, Context context) {
            if (user.isFollowedByCurrentUser()) {
                followButton.setText(R.string.user_discovery_following);
                followButton.setIconResource(R.drawable.ic_check);
                followButton.setStrokeColor(ContextCompat.getColorStateList(context, R.color.primaryColor));
                followButton.setTextColor(ContextCompat.getColor(context, R.color.primaryColor));
                // Change style maybe? (e.g., setBackgroundTint)
            } else {
                followButton.setText(R.string.user_discovery_follow);
                followButton.setIconResource(R.drawable.ic_add);
                followButton.setStrokeColor(ContextCompat.getColorStateList(context, R.color.subtleTextColor));
                followButton.setTextColor(ContextCompat.getColor(context, R.color.textColor));
                // Revert style if changed
            }
            followButton.setEnabled(true); // Ensure button is enabled after update
        }
    }
}