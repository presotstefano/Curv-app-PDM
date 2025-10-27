package com.spstudio.curv_app.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast; // Placeholder

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.EventModel;
import com.spstudio.curv_app.services.SettingsService;
// import com.spstudio.curv_app.ui.activity.EventDetailActivity; // Da creare

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private List<EventModel> events;
    private final Context context;
    private final SettingsService settingsService;

    public EventAdapter(Context context, List<EventModel> events) {
        this.context = context;
        this.events = (events != null) ? events : new ArrayList<>();
        this.settingsService = SettingsService.getInstance(context); // Per formattare distanza
    }

    public void updateData(List<EventModel> newEvents) {
        this.events.clear();
        if (newEvents != null) {
            this.events.addAll(newEvents);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        EventModel event = events.get(position);
        holder.bind(event, context, settingsService);

        // Click sull'intera card per andare al dettaglio evento
        holder.itemView.setOnClickListener(v -> {
            // TODO: Navigare a EventDetailActivity
            // Intent intent = new Intent(context, EventDetailActivity.class);
            // intent.putExtra("EVENT_ID", event.getId());
            // context.startActivity(intent);
            Toast.makeText(context, "View event: " + event.getName(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    // --- ViewHolder ---
    static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView eventBannerImageView;
        TextView eventNameTextView;
        TextView dateTimeTextView;
        TextView routeInfoTextView;
        TextView participantsTextView;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            eventBannerImageView = itemView.findViewById(R.id.eventBannerImageView);
            eventNameTextView = itemView.findViewById(R.id.eventNameTextView);
            dateTimeTextView = itemView.findViewById(R.id.dateTimeTextView);
            routeInfoTextView = itemView.findViewById(R.id.routeInfoTextView);
            participantsTextView = itemView.findViewById(R.id.participantsTextView);
        }

        public void bind(EventModel event, Context context, SettingsService settingsService) {
            eventNameTextView.setText(event.getName());

            // Formatta Data e Ora
            if (event.getDateTime() != null) {
                Date date = event.getDateTime().toDate();
                // Puoi usare formati più specifici o librerie come JodaTime/ThreeTenABP
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                dateTimeTextView.setText(sdf.format(date));
            } else {
                dateTimeTextView.setText("Date TBD");
            }

            // Info Percorso (usa campi denormalizzati)
            String routeName = event.getRouteName() != null ? event.getRouteName() : "Unknown Route";
            String distanceFormatted = settingsService.formatDistance(event.getRouteDistanceKm());
            routeInfoTextView.setText(context.getString(R.string.event_detail_route_info, routeName, distanceFormatted)); // Crea questa stringa: "Route: %1$s (%2$s)"

            // Partecipanti (usa campo contatore)
            int count = event.getParticipantCount();
            participantsTextView.setText(context.getResources().getQuantityString(R.plurals.participant_count, count, count)); // Crea questa risorsa plurals

            // Carica Banner (se esiste)
            String bannerUrl = event.getBannerUrl();
            if (bannerUrl != null && !bannerUrl.isEmpty()) {
                eventBannerImageView.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(bannerUrl)
                        .centerCrop()
                        .placeholder(R.color.backgroundColor) // Placeholder
                        .error(R.color.borderColor) // Immagine errore
                        .into(eventBannerImageView);
            } else {
                eventBannerImageView.setVisibility(View.GONE);
            }
        }
    }
}