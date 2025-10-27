package com.spstudio.curv_app.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.BottomSheetAddReviewBinding; // Importa il binding del layout

import java.util.HashMap;
import java.util.Map;

public class AddReviewBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "AddReviewBottomSheet";
    private static final String ARG_ROUTE_ID = "routeId";

    private BottomSheetAddReviewBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String routeId;
    private boolean isLoading = false;

    // Metodo statico "new instance" per passare l'ID del percorso
    public static AddReviewBottomSheet newInstance(String routeId) {
        AddReviewBottomSheet fragment = new AddReviewBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ROUTE_ID, routeId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            routeId = getArguments().getString(ARG_ROUTE_ID);
        }
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAddReviewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.submitReviewButton.setOnClickListener(v -> submitReview());
    }

    private void submitReview() {
        if (isLoading) return;

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "You must be logged in to review.", Toast.LENGTH_SHORT).show();
            return;
        }

        float rating = binding.ratingBar.getRating();
        if (rating == 0) {
            Toast.makeText(getContext(), R.string.route_detail_review_rating_error, Toast.LENGTH_SHORT).show();
            return;
        }

        String comment = binding.commentEditText.getText().toString().trim();
        setLoading(true);

        // Recupera i dati dell'utente per salvarli nella recensione
        db.collection("utenti").document(user.getUid()).get()
                .addOnSuccessListener(userDoc -> {
                    String userName = user.getDisplayName(); // Prova a prenderlo da Auth
                    if (userDoc.exists() && userDoc.getString("nomeUtente") != null) {
                        userName = userDoc.getString("nomeUtente"); // Prendi da Firestore se esiste
                    } else if (userName == null || userName.isEmpty()) {
                        userName = "Anonymous Pilot"; // Fallback
                    }

                    Map<String, Object> reviewData = new HashMap<>();
                    reviewData.put("userId", user.getUid());
                    reviewData.put("userName", userName);
                    reviewData.put("rating", (int) rating); // Salva come intero
                    reviewData.put("commento", comment);
                    reviewData.put("timestamp", com.google.firebase.Timestamp.now());
                    reviewData.put("replyCount", 0); // Inizializza contatore risposte

                    // Salva la recensione usando l'UID dell'utente come ID documento
                    // Questo assicura che un utente possa lasciare solo una recensione
                    db.collection("percorsi").document(routeId)
                            .collection("recensioni").document(user.getUid())
                            .set(reviewData)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Review submitted successfully.");
                                Toast.makeText(getContext(), R.string.route_detail_review_success, Toast.LENGTH_SHORT).show();
                                setLoading(false);
                                dismiss(); // Chiudi il bottom sheet
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error submitting review", e);
                                Toast.makeText(getContext(), getString(R.string.route_detail_review_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                                setLoading(false);
                            });
                })
                .addOnFailureListener(e -> {
                    // Fallimento nel recuperare i dati utente
                    Log.e(TAG, "Error fetching user data for review", e);
                    Toast.makeText(getContext(), getString(R.string.route_detail_review_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                    setLoading(false);
                });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        binding.progressBarReview.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.submitReviewButton.setEnabled(!loading);
        binding.ratingBar.setEnabled(!loading);
        binding.commentEditText.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Pulisci il binding
    }
}