package com.spstudio.curv_app.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity; // Per impostare Toolbar
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.FragmentCommunityBinding; // Usa ViewBinding
import com.spstudio.curv_app.databinding.IncludeCommunityLinkItemBinding; // Binding per il link
import com.spstudio.curv_app.databinding.IncludeStatCardVerticalBinding; // Binding per stat card
import com.spstudio.curv_app.ui.activity.EventDiscoveryActivity;
import com.spstudio.curv_app.ui.activity.UserDiscoveryActivity;

import java.util.Locale;

public class CommunityFragment extends Fragment {

    private static final String TAG = "CommunityFragment";
    private FragmentCommunityBinding binding;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCommunityBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Imposta la Toolbar specifica per questo fragment (opzionale)
        // Se usi la Toolbar definita nel layout del fragment
        // ((AppCompatActivity) requireActivity()).setSupportActionBar(binding.toolbar);

        // Imposta i link di navigazione
        setupNavigationLinks();

        // Carica le statistiche aggregate
        loadAggregateStats();
    }

    private void setupNavigationLinks() {
        // Usa il binding per accedere ai layout inclusi
        setupLinkItem(binding.feedLink, R.drawable.ic_feed, R.string.community_link_activity_feed, () -> {
            // TODO: Navigare a FeedActivity
            Toast.makeText(getContext(), R.string.community_link_activity_feed, Toast.LENGTH_SHORT).show();
        });
        setupLinkItem(binding.groupsLink, R.drawable.ic_groups, R.string.community_link_discover_groups, () -> {
            // TODO: Navigare a GroupsDiscoveryActivity
            Toast.makeText(getContext(), R.string.community_link_discover_groups, Toast.LENGTH_SHORT).show();
        });
        setupLinkItem(binding.eventsLink, R.drawable.ic_event, R.string.community_link_join_events, () -> {
            // === MODIFICA QUI ===
            Intent intent = new Intent(requireActivity(), EventDiscoveryActivity.class);
            startActivity(intent);
            // Toast.makeText(getContext(), R.string.community_link_join_events, Toast.LENGTH_SHORT).show(); // Rimuovi placeholder
            // === FINE MODIFICA ===
        });
        setupLinkItem(binding.pilotsLink, R.drawable.ic_search, R.string.community_link_discover_pilots, () -> {
            // === MODIFICA QUI ===
            Intent intent = new Intent(requireActivity(), UserDiscoveryActivity.class);
            startActivity(intent);
            // Toast.makeText(getContext(), R.string.community_link_discover_pilots, Toast.LENGTH_SHORT).show();
            // === FINE MODIFICA ===
        });

        // Imposta i listener sulle Card esterne
        binding.feedLinkCard.setOnClickListener(v -> binding.feedLink.getRoot().performClick());
        binding.groupsLinkCard.setOnClickListener(v -> binding.groupsLink.getRoot().performClick());
        binding.eventsLinkCard.setOnClickListener(v -> binding.eventsLink.getRoot().performClick());
        binding.pilotsLinkCard.setOnClickListener(v -> binding.pilotsLink.getRoot().performClick());
    }

    // Helper per configurare un item della lista di link
    private void setupLinkItem(IncludeCommunityLinkItemBinding linkBinding, int iconRes, int textRes, Runnable onClickAction) {
        linkBinding.linkIcon.setImageResource(iconRes);
        linkBinding.linkText.setText(textRes);
        linkBinding.getRoot().setOnClickListener(v -> onClickAction.run());
    }


    // Carica le statistiche (conteggio documenti - semplice, può essere impreciso/costoso)
    private void loadAggregateStats() {
        // Mostra placeholder o 0 inizialmente
        updateStatCard(binding.pilotsStatCard, R.drawable.ic_directions_car, "-", getString(R.string.community_stat_pilots), R.color.stat_blue);
        updateStatCard(binding.routesStatCard, R.drawable.ic_alt_route, "-", getString(R.string.community_stat_routes), R.color.stat_green);
        updateStatCard(binding.eventsStatCard, R.drawable.ic_event, "-", getString(R.string.community_stat_events), R.color.stat_purple);

        // Conteggio Piloti (utenti)
        db.collection("utenti").count().get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (isAdded() && binding != null)
                        updateStatCard(binding.pilotsStatCard, R.drawable.ic_directions_car, String.valueOf(snapshot.getCount()), getString(R.string.community_stat_pilots), R.color.stat_blue);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error counting users", e);
                    if (isAdded() && binding != null)
                        updateStatCard(binding.pilotsStatCard, R.drawable.ic_directions_car, "!", getString(R.string.community_stat_pilots), R.color.dangerColor);
                });

        // Conteggio Percorsi (solo approvati)
        db.collection("percorsi").whereEqualTo("status", "approved").count().get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (isAdded() && binding != null)
                        updateStatCard(binding.routesStatCard, R.drawable.ic_alt_route, String.valueOf(snapshot.getCount()), getString(R.string.community_stat_routes), R.color.stat_green);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error counting routes", e);
                    if (isAdded() && binding != null)
                        updateStatCard(binding.routesStatCard, R.drawable.ic_alt_route, "!", getString(R.string.community_stat_routes), R.color.dangerColor);
                });

        // Conteggio Eventi
        db.collection("events").count().get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (isAdded() && binding != null)
                        updateStatCard(binding.eventsStatCard, R.drawable.ic_event, String.valueOf(snapshot.getCount()), getString(R.string.community_stat_events), R.color.stat_purple);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error counting events", e);
                    if (isAdded() && binding != null)
                        updateStatCard(binding.eventsStatCard, R.drawable.ic_event, "!", getString(R.string.community_stat_events), R.color.dangerColor);
                });

        // NOTA: Il conteggio diretto con .count() è efficiente ma richiede permessi di lettura
        // sulla collezione. Per statistiche aggiornate in tempo reale o più complesse,
        // è meglio usare Cloud Functions che mantengono documenti contatore separati.
    }

    // Riutilizziamo l'helper per le Stat Card Verticali
    private void updateStatCard(IncludeStatCardVerticalBinding statBinding, int iconRes, String value, String label, int colorRes) {
        Context context = getContext();
        if (context == null || statBinding == null) return; // Controllo sicurezza

        int color = ContextCompat.getColor(context, colorRes);
        statBinding.getRoot().setStrokeColor(color & 0x4DFFFFFF);
        statBinding.getRoot().setCardBackgroundColor(color & 0x1AFFFFFF);
        statBinding.statIcon.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(statBinding.statIcon, ColorStateList.valueOf(color));
        statBinding.statValue.setText(value);
        statBinding.statValue.setTextColor(color);
        statBinding.statLabel.setText(label);
        statBinding.statLabel.setTextColor(ContextCompat.getColor(context, R.color.subtleTextColor));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Pulisci ViewBinding
    }
}