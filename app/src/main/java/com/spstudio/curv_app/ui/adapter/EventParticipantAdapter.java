package com.spstudio.curv_app.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast; // Placeholder

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.UserModel; // Riutilizziamo UserModel
// import com.spstudio.curv_app.ui.activity.UserProfileActivity;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class EventParticipantAdapter extends RecyclerView.Adapter<EventParticipantAdapter.ParticipantViewHolder> {

    private List<UserModel> participants = new ArrayList<>();
    private final Context context;

    public EventParticipantAdapter(Context context) {
        this.context = context;
    }

    public void updateData(List<UserModel> newParticipants) {
        this.participants.clear();
        if (newParticipants != null) {
            this.participants.addAll(newParticipants);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ParticipantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_participant, parent, false);
        return new ParticipantViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ParticipantViewHolder holder, int position) {
        UserModel participant = participants.get(position);
        holder.bind(participant, context);

        holder.itemView.setOnClickListener(v -> {
            // TODO: Navigare a UserProfileActivity
            // Intent intent = new Intent(context, UserProfileActivity.class);
            // intent.putExtra("USER_ID", participant.getUid());
            // context.startActivity(intent);
            Toast.makeText(context, "View profile: " + participant.getNomeUtente(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return participants.size();
    }

    static class ParticipantViewHolder extends RecyclerView.ViewHolder {
        CircleImageView avatarImageView;
        TextView nameTextView;

        public ParticipantViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.participantAvatarImageView);
            nameTextView = itemView.findViewById(R.id.participantNameTextView);
        }

        public void bind(UserModel participant, Context context) {
            nameTextView.setText(participant.getNomeUtente() != null ? participant.getNomeUtente() : context.getString(R.string.user_profile_unnamed_pilot));
            Glide.with(context)
                    .load(participant.getProfileImageUrl())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(avatarImageView);
        }
    }
}