package com.spstudio.curv_app.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.RouteModel;
import com.spstudio.curv_app.databinding.FragmentMyRoutesBinding; // Usa ViewBinding
import com.spstudio.curv_app.ui.adapter.MyRoutesAdapter;

import java.util.ArrayList;
import java.util.List;

public class MyRoutesFragment extends Fragment implements MyRoutesAdapter.OnRouteActionListener {

    private static final String TAG = "MyRoutesFragment";

    private FragmentMyRoutesBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    private MyRoutesAdapter adapter;
    private final List<RouteModel> myRoutes = new ArrayList<>();

    // Se questo fragment viene usato per mostrare i percorsi di un ALTRO utente (da ProfileFragment)
    private String userIdToShow;
    private boolean isMyOwnProfile;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        // Determina se mostrare i percorsi dell'utente loggato o di un altro utente
        if (getArguments() != null && getArguments().containsKey("USER_ID")) {
            userIdToShow = getArguments().getString("USER_ID");
            isMyOwnProfile = userIdToShow.equals(currentUser.getUid());
        } else {
            userIdToShow = currentUser.getUid();
            isMyOwnProfile = true;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMyRoutesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();

        // Imposta SwipeRefreshLayout
        binding.swipeRefreshLayout.setOnRefreshListener(this::fetchMyRoutes);
        binding.swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.primaryColor));

        // Carica i dati
        fetchMyRoutes();
    }

    private void setupRecyclerView() {
        // === MODIFICA QUI ===
        // Non passare la lista "myRoutes" al costruttore
        adapter = new MyRoutesAdapter(requireContext(), this);
        // === FINE MODIFICA ===
        binding.myRoutesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.myRoutesRecyclerView.setAdapter(adapter);
    }

    private void fetchMyRoutes() {
        if (userIdToShow == null) {
            Log.e(TAG, "Cannot fetch routes: userIdToShow is NULL.");
            binding.progressBar.setVisibility(View.GONE);
            updateEmptyState();
            return;
        }

        // === LOG AGGIUNTO ===
        Log.d(TAG, "Fetching routes for user ID: " + userIdToShow);
        // ====================

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.myRoutesRecyclerView.setVisibility(View.GONE);
        binding.emptyStateLayout.setVisibility(View.GONE);

        Query query = db.collection("percorsi")
                .whereEqualTo("creatoreUid", userIdToShow)
                .orderBy("dataCreazione", Query.Direction.DESCENDING);

        if (!isMyOwnProfile) {
            query = query.whereEqualTo("status", "approved");
        }

        query.get().addOnCompleteListener(task -> {
            if (!isAdded()) return;

            binding.progressBar.setVisibility(View.GONE);
            binding.swipeRefreshLayout.setRefreshing(false);

            if (task.isSuccessful()) {
                myRoutes.clear();
                if (task.getResult() != null) {
                    // === LOG AGGIUNTO ===
                    Log.d(TAG, "Query successful! Found " + task.getResult().size() + " documents.");
                    // ====================
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        // === LOG AGGIUNTO ===
                        Log.d(TAG, "  -> Processing route: " + doc.getId() + " with name: " + doc.getString("nome"));
                        // ====================
                        try {
                            myRoutes.add(RouteModel.fromFirestore(doc));
                        } catch (Exception e) {
                            // === LOG AGGIUNTO per errori di parsing ===
                            Log.e(TAG, "Error parsing document " + doc.getId(), e);
                            // ======================================
                        }
                    }
                } else {
                    Log.w(TAG, "Query successful, but result is null.");
                }
                adapter.updateData(myRoutes);
                updateEmptyState();
            } else {
                Log.e(TAG, "Error fetching routes: ", task.getException());
                Toast.makeText(getContext(), "Error loading routes", Toast.LENGTH_SHORT).show();
                updateEmptyState();
            }
        });
    }

    // Mostra/Nascondi lo stato vuoto
    private void updateEmptyState() {
        if (myRoutes.isEmpty()) {
            binding.myRoutesRecyclerView.setVisibility(View.GONE);
            binding.emptyStateLayout.setVisibility(View.VISIBLE);
            // Cambia testo se si guarda il profilo di un altro utente
            if (!isMyOwnProfile) {
                binding.emptyStateTitle.setText(R.string.my_routes_empty_title_other_user);
                binding.emptyStateSubtitle.setVisibility(View.GONE); // Nascondi sottotitolo
            } else {
                binding.emptyStateTitle.setText(R.string.my_routes_empty_title);
                binding.emptyStateSubtitle.setVisibility(View.VISIBLE);
                binding.emptyStateSubtitle.setText(R.string.my_routes_empty_subtitle);
            }
        } else {
            binding.myRoutesRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyStateLayout.setVisibility(View.GONE);
        }
    }

    // Implementazione dei metodi dell'interfaccia OnRouteActionListener

    @Override
    public void onRouteDelete(RouteModel route) {
        if (!isMyOwnProfile) return; // Sicurezza: solo il proprietario può eliminare

        binding.progressBar.setVisibility(View.VISIBLE); // Mostra caricamento

        db.collection("percorsi").document(route.getId()).delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Route deleted: " + route.getId());
                    Toast.makeText(getContext(), R.string.my_routes_delete_success, Toast.LENGTH_SHORT).show();
                    fetchMyRoutes(); // Ricarica la lista
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting route", e);
                    if (isAdded()) binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), getString(R.string.my_routes_delete_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onShowRejectionReason(String reason) {
        // Mostra un semplice AlertDialog con la motivazione
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.my_routes_rejection_reason_title)
                .setMessage(TextUtils.isEmpty(reason) ? getString(R.string.my_routes_rejection_reason_default) : reason)
                .setPositiveButton(R.string.my_routes_rejection_reason_ok, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Pulisci il ViewBinding
    }
}