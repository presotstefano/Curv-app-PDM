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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.GroupModel; // Dovrai creare questo modello
import com.spstudio.curv_app.databinding.BottomSheetAddToGroupBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AddToGroupBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "AddToGroupSheet";
    private static final String ARG_ROUTE_ID = "routeId";

    private BottomSheetAddToGroupBinding binding;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String routeId;

    private GroupSelectionAdapter adapter;
    private final List<GroupModel> adminGroups = new ArrayList<>();
    private final Set<String> routeInGroupsSet = new HashSet<>(); // Per tracciare lo stato

    public static AddToGroupBottomSheet newInstance(String routeId) {
        AddToGroupBottomSheet fragment = new AddToGroupBottomSheet();
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
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAddToGroupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        loadAdminGroups();
    }

    private void setupRecyclerView() {
        adapter = new GroupSelectionAdapter(getContext(), adminGroups, routeInGroupsSet, this::onGroupToggled);
        binding.groupsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.groupsRecyclerView.setAdapter(adapter);
    }

    // Carica i gruppi di cui l'utente è admin E controlla se il percorso è in quei gruppi
    private void loadAdminGroups() {
        if (currentUser == null) {
            binding.progressBarGroups.setVisibility(View.GONE);
            binding.emptyTextView.setVisibility(View.VISIBLE);
            return;
        }

        binding.progressBarGroups.setVisibility(View.VISIBLE);
        binding.emptyTextView.setVisibility(View.GONE);
        binding.groupsRecyclerView.setVisibility(View.GONE);

        // 1. Trova i gruppi di cui l'utente è admin
        db.collection("groups")
                .whereEqualTo("creatorUid", currentUser.getUid()) // Semplificazione: carica solo i gruppi creati
                // Nota: La logica Flutter cerca 'role' == 'admin' nelle sottocollezioni,
                // che è una query complessa. Per Java, iniziamo con i gruppi *creati* dall'utente.
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    adminGroups.clear();
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "User is not admin of any groups.");
                        binding.progressBarGroups.setVisibility(View.GONE);
                        binding.emptyTextView.setVisibility(View.VISIBLE);
                        return;
                    }

                    List<String> groupIds = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        adminGroups.add(GroupModel.fromFirestore(doc)); // Richiede GroupModel.java
                        groupIds.add(doc.getId());
                    }

                    // 2. Controlla in quali di questi gruppi il percorso è già presente
                    checkRouteMembership(groupIds);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching admin groups", e);
                    binding.progressBarGroups.setVisibility(View.GONE);
                    binding.emptyTextView.setVisibility(View.VISIBLE);
                });
    }

    private void checkRouteMembership(List<String> groupIds) {
        if (groupIds.isEmpty()) {
            binding.progressBarGroups.setVisibility(View.GONE);
            binding.groupsRecyclerView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
            return;
        }

        // Firestore non supporta query "IN" su percorsi di documenti diversi
        // Dobbiamo controllare un gruppo alla volta (o parallelizzare)
        routeInGroupsSet.clear();
        int[] tasksCompleted = {0}; // Contatore atomico (quasi)

        for (String groupId : groupIds) {
            db.collection("groups").document(groupId)
                    .collection("favoriteRoutes").document(routeId)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult().exists()) {
                            routeInGroupsSet.add(groupId);
                        }
                        // Controlla se tutte le query sono finite
                        tasksCompleted[0]++;
                        if (tasksCompleted[0] == groupIds.size()) {
                            Log.d(TAG, "Finished checking route membership.");
                            binding.progressBarGroups.setVisibility(View.GONE);
                            binding.groupsRecyclerView.setVisibility(View.VISIBLE);
                            adapter.notifyDataSetChanged();
                        }
                    });
        }
    }


    // Chiamato dall'adapter quando uno switch viene cliccato
    private void onGroupToggled(GroupModel group, boolean isChecked) {
        String groupId = group.getId();
        Log.d(TAG, "Toggling route " + routeId + " in group " + groupId + " to " + isChecked);

        com.google.firebase.firestore.DocumentReference routeRef = db.collection("groups").document(groupId)
                .collection("favoriteRoutes").document(routeId);

        Task<Void> task;
        String successMessage;
        String errorMessage = getString(R.string.route_detail_group_error);

        if (isChecked) {
            // Aggiungi
            Map<String, Object> data = new HashMap<>();
            data.put("addedAt", com.google.firebase.Timestamp.now());
            task = routeRef.set(data);
            successMessage = getString(R.string.route_detail_group_add_success);
        } else {
            // Rimuovi
            task = routeRef.delete();
            successMessage = getString(R.string.route_detail_group_remove_success);
        }

        task.addOnSuccessListener(aVoid -> {
            Toast.makeText(getContext(), successMessage, Toast.LENGTH_SHORT).show();
            // Aggiorna il set locale in caso di successo
            if (isChecked) routeInGroupsSet.add(groupId);
            else routeInGroupsSet.remove(groupId);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error toggling route in group", e);
            Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            // Ripristina lo stato visivo dello switch (l'adapter deve essere notificato)
            adapter.notifyItemChanged(adminGroups.indexOf(group));
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // --- Adapter Interno ---
    private static class GroupSelectionAdapter extends RecyclerView.Adapter<GroupSelectionAdapter.GroupViewHolder> {

        private final Context context;
        private final List<GroupModel> groups;
        private final Set<String> routeInGroupsSet;
        private final OnGroupToggleListener listener;

        interface OnGroupToggleListener {
            void onToggle(GroupModel group, boolean isChecked);
        }

        GroupSelectionAdapter(Context context, List<GroupModel> groups, Set<String> routeInGroupsSet, OnGroupToggleListener listener) {
            this.context = context;
            this.groups = groups;
            this.routeInGroupsSet = routeInGroupsSet;
            this.listener = listener;
        }

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_group_selection, parent, false);
            return new GroupViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            GroupModel group = groups.get(position);
            holder.bind(group, routeInGroupsSet.contains(group.getId()), listener);
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }

        static class GroupViewHolder extends RecyclerView.ViewHolder {
            SwitchMaterial groupSwitch;

            public GroupViewHolder(@NonNull View itemView) {
                super(itemView);
                groupSwitch = itemView.findViewById(R.id.groupSwitch);
            }

            public void bind(GroupModel group, boolean isChecked, OnGroupToggleListener listener) {
                groupSwitch.setText(group.getName());
                // Rimuovi il listener precedente per evitare chiamate multiple
                groupSwitch.setOnCheckedChangeListener(null);
                // Imposta lo stato corrente
                groupSwitch.setChecked(isChecked);
                // Aggiungi il nuovo listener
                groupSwitch.setOnCheckedChangeListener((buttonView, isNowChecked) -> {
                    listener.onToggle(group, isNowChecked);
                });
            }
        }
    }
}