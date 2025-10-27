package com.spstudio.curv_app.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.Review;
import com.spstudio.curv_app.ui.activity.UserProfileActivity;
// import com.spstudio.curv_app.ui.activity.UserProfileActivity;

import java.util.ArrayList;
import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private List<Review> reviews;
    private final Context context;
    private final String routeId;

    public ReviewAdapter(Context context, String routeId, List<Review> reviews) {
        this.context = context;
        this.routeId = routeId;
        this.reviews = (reviews != null) ? reviews : new ArrayList<>();
    }

    public void updateData(List<Review> newReviews) {
        this.reviews.clear();
        if (newReviews != null) {
            this.reviews.addAll(newReviews);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_review, parent, false);
        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        Review review = reviews.get(position);
        holder.bind(review, context);

        holder.replyButton.setOnClickListener(v -> {
            Toast.makeText(context, "Reply to review " + review.getId(), Toast.LENGTH_SHORT).show();
        });

        if (review.getReplyCount() > 0) {
            holder.viewRepliesButton.setVisibility(View.VISIBLE);

            // === CORREZIONE QUI ===
            // Usa getQuantityString per gestire correttamente singolare e plurale
            int replyCount = review.getReplyCount();
            holder.viewRepliesButton.setText(
                    context.getResources().getQuantityString(R.plurals.view_replies_count, replyCount, replyCount)
            );
            // === FINE CORREZIONE ===

            holder.viewRepliesButton.setOnClickListener(v -> {
                Toast.makeText(context, "Toggle replies for " + review.getId(), Toast.LENGTH_SHORT).show();
            });
        } else {
            holder.viewRepliesButton.setVisibility(View.GONE);
        }

        holder.usernameTextView.setOnClickListener(v -> navigateToUserProfile(review.getUserId()));
    }

    private void navigateToUserProfile(String userId) {
        if (userId != null && !userId.isEmpty()) {
            // === SCOMMENTA E VERIFICA QUESTE RIGHE ===
            Intent intent = new Intent(context, UserProfileActivity.class);
            intent.putExtra("USER_ID", userId); // Passa l'ID dell'autore della recensione
            context.startActivity(intent);
            // Toast.makeText(context, "Go to profile ID: " + userId, Toast.LENGTH_SHORT).show(); // Rimuovi Toast
            // === FINE MODIFICA ===
        }
    }

    @Override
    public int getItemCount() {
        return reviews.size();
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        TextView usernameTextView;
        LinearLayout ratingStarsLayout;
        TextView commentTextView;
        MaterialButton replyButton;
        MaterialButton viewRepliesButton;
        LinearLayout repliesContainer;

        public ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            usernameTextView = itemView.findViewById(R.id.usernameTextView);
            ratingStarsLayout = itemView.findViewById(R.id.ratingStarsLayout);
            commentTextView = itemView.findViewById(R.id.commentTextView);
            replyButton = itemView.findViewById(R.id.replyButton);
            viewRepliesButton = itemView.findViewById(R.id.viewRepliesButton);
            repliesContainer = itemView.findViewById(R.id.repliesContainer);
        }

        public void bind(Review review, Context context) {
            usernameTextView.setText(review.getUserName());

            ratingStarsLayout.removeAllViews();
            int rating = review.getRating();
            int starColor = ContextCompat.getColor(context, R.color.ratingColor);
            for (int i = 0; i < 5; i++) {
                ImageView star = new ImageView(context);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        (int) context.getResources().getDimension(R.dimen.star_size_small),
                        (int) context.getResources().getDimension(R.dimen.star_size_small));
                if (i > 0) params.setMarginStart((int) context.getResources().getDimension(R.dimen.star_margin_small));
                star.setLayoutParams(params);
                star.setImageResource(i < rating ? R.drawable.ic_star : R.drawable.ic_star_border);
                star.setColorFilter(starColor);
                ratingStarsLayout.addView(star);
            }

            if (!TextUtils.isEmpty(review.getCommento())) {
                commentTextView.setText(review.getCommento());
                commentTextView.setVisibility(View.VISIBLE);
            } else {
                commentTextView.setVisibility(View.GONE);
            }

            repliesContainer.setVisibility(View.GONE);
            repliesContainer.removeAllViews();
        }
    }
}
