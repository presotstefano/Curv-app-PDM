package com.spstudio.curv_app.ui.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.RouteModel;
import com.spstudio.curv_app.databinding.FragmentMyRoutesBinding; // RIUTILIZZIAMO lo stesso layout di MyRoutes
import com.spstudio.curv_app.ui.adapter.MyRoutesAdapter; // RIUTILIZZIAMO lo stesso adapter

import java.util.ArrayList;
import java.util.List;

public class SavedRoutesFragment extends Fragment implements MyRoutesAdapter.OnRouteActionListener {
    // Nota: L'interfaccia OnRouteActionListener è qui solo per compatibilità con l'adapter
    // Non implementeremo l'eliminazione o la visualizzazione dei motivi di rifiuto da qui.

    private static final String TAG = "SavedRoutesFragment";
    private static final String ARG_USER_ID = "USER_ID";

    private FragmentMyRoutesBinding binding; // Riutilizziamo il binding di MyRoutes
    private FirebaseFirestore db;
    private String userId;

    private MyRoutesAdapter adapter;
    private final List<RouteModel> savedRoutes = new ArrayList<>();

    // Factory method per passare l'ID utente
    public static SavedRoutesFragment newInstance(String userId) {
        SavedRoutesFragment fragment = new SavedRoutesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        if (getArguments() != null) {
            userId = getArguments().getString(ARG_USER_ID);
        } else {
            // Fallback, anche se non dovrebbe succedere
            userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Riutilizziamo lo stesso layout di MyRoutesFragment
        binding = FragmentMyRoutesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        binding.swipeRefreshLayout.setOnRefreshListener(this::fetchSavedRoutes);
        binding.swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.primaryColor));

        fetchSavedRoutes();
    }

    private void setupRecyclerView() {
        // === CORREZIONE QUI ===
        // Rimuovi 'savedRoutes' dalla chiamata al costruttore
        adapter = new MyRoutesAdapter(requireContext(), this);
        // === FINE CORREZIONE ===
        binding.myRoutesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.myRoutesRecyclerView.setAdapter(adapter);
    }

    private void fetchSavedRoutes() {
        if (userId == null) {
            Log.e(TAG, "Cannot fetch saved routes: User ID is null");
            updateEmptyState();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.myRoutesRecyclerView.setVisibility(View.GONE);
        binding.emptyStateLayout.setVisibility(View.GONE);

        // 1. Ottieni gli ID dei percorsi salvati dall'utente
        db.collection("utenti").document(userId).collection("savedRoutes")
                .orderBy("savedAt", Query.Direction.DESCENDING) // Ordina per data salvataggio
                .get()
                .addOnSuccessListener(savedSnapshot -> {
                    if (savedSnapshot.isEmpty()) {
                        Log.d(TAG, "User has no saved routes.");
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefreshLayout.setRefreshing(false);
                        savedRoutes.clear();
                        adapter.updateData(savedRoutes);
                        updateEmptyState();
                        return;
                    }

                    List<String> routeIds = new ArrayList<>();
                    for (DocumentSnapshot doc : savedSnapshot.getDocuments()) {
                        routeIds.add(doc.getId());
                    }

                    // 2. Recupera i documenti dei percorsi completi usando gli ID
                    // Firestore limita le query 'whereIn' a 10 elementi (ora 30, ma meglio andare sicuri con batch)
                    // Per semplicità, assumiamo meno di 10-30 percorsi salvati.
                    // Per una soluzione scalabile, dovresti dividere 'routeIds' in blocchi di 10.
                    if (routeIds.isEmpty()) return; // Prevenzione

                    if (routeIds.isEmpty()) return; // Prevenzione
                    db.collection("percorsi").whereIn(FieldPath.documentId(), routeIds) // <-- Ora FieldPath verrà riconosciuto
                            .get()
                            .addOnSuccessListener(routesSnapshot-> {
                                if (!isAdded()) return;
                                binding.progressBar.setVisibility(View.GONE);
                                binding.swipeRefreshLayout.setRefreshing(false);

                                savedRoutes.clear();
                                for (DocumentSnapshot doc : routesSnapshot.getDocuments()) {
                                    savedRoutes.add(RouteModel.fromFirestore(doc));
                                }
                                adapter.updateData(savedRoutes);
                                updateEmptyState();
                                Log.d(TAG, "Fetched " + savedRoutes.size() + " saved routes.");
                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;
                                Log.e(TAG, "Error fetching full route documents", e);
                                binding.progressBar.setVisibility(View.GONE);
                                binding.swipeRefreshLayout.setRefreshing(false);
                                updateEmptyState();
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Error fetching saved route IDs", e);
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefreshLayout.setRefreshing(false);
                    updateEmptyState();
                });
    }

    private void updateEmptyState() {
        if (savedRoutes.isEmpty()) {
            binding.myRoutesRecyclerView.setVisibility(View.GONE);
            binding.emptyStateLayout.setVisibility(View.VISIBLE);
            // Imposta testi specifici per i percorsi salvati
            binding.emptyStateTitle.setText(R.string.saved_routes_empty_title);
            binding.emptyStateSubtitle.setText(R.string.saved_routes_empty_subtitle);
            binding.emptyStateSubtitle.setVisibility(View.VISIBLE);
        } else {
            binding.myRoutesRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyStateLayout.setVisibility(View.GONE);
        }
    }

    // --- Implementazione vuota (o semplificata) dell'interfaccia ---
    @Override
    public void onRouteDelete(RouteModel route) {
        // L'utente non dovrebbe eliminare un percorso dalla lista "Salvati"
        // Al massimo, dovrebbe "Rimuovere dai salvati" (unsave)
        Toast.makeText(getContext(), "Unsave action to be implemented", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onShowRejectionReason(String reason) {
        // Non applicabile ai percorsi salvati (sono già approvati)
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}