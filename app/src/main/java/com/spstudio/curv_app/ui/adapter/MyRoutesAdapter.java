package com.spstudio.curv_app.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Log; // Aggiungi questo import per i log
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.RouteModel;
import com.spstudio.curv_app.databinding.IncludeInfoChipBinding;
import com.spstudio.curv_app.services.SettingsService;
// import com.spstudio.curv_app.ui.activity.RouteDetailActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MyRoutesAdapter extends RecyclerView.Adapter<MyRoutesAdapter.RouteViewHolder> {

    // === MODIFICA CHIAVE 1: Rendi la lista 'final' e inizializzala qui ===
    private final List<RouteModel> routes = new ArrayList<>();
    private final Context context;
    private final SettingsService settingsService;
    private final OnRouteActionListener actionListener;

    public interface OnRouteActionListener {
        void onRouteDelete(RouteModel route);
        void onShowRejectionReason(String reason);
    }

    public MyRoutesAdapter(Context context, OnRouteActionListener listener) {
        this.context = context;
        // this.routes = (routes != null) ? routes : new ArrayList<>(); // Rimuovi questo
        this.settingsService = SettingsService.getInstance(context);
        this.actionListener = listener;
    }

    // === MODIFICA CHIAVE 2: Il metodo updateData ora gestisce la logica ===
    public void updateData(List<RouteModel> newRoutes) {
        Log.d("MyRoutesAdapter", "Adapter updateData called with " + (newRoutes != null ? newRoutes.size() : 0) + " routes.");

        this.routes.clear(); // 1. Pulisci la lista interna (che è separata)
        if (newRoutes != null) {
            this.routes.addAll(newRoutes); // 2. Aggiungi tutti i nuovi elementi
        }

        // 3. Notifica al RecyclerView che i dati sono cambiati
        notifyDataSetChanged(); // Assicura che la UI si aggiorni

        Log.d("MyRoutesAdapter", "Adapter internal list size is now: " + this.routes.size());
    }
    // === FINE MODIFICHE ===

    @NonNull
    @Override
    public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_my_route, parent, false);
        return new RouteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
        RouteModel route = routes.get(position);
        holder.bind(route, context, settingsService, actionListener);
    }

    @Override
    public int getItemCount() {
        Log.d("MyRoutesAdapter", "getItemCount() called, returning: " + routes.size());
        return routes.size();
    }

    // --- ViewHolder (Invariato) ---
    static class RouteViewHolder extends RecyclerView.ViewHolder {

        ImageView staticMapImageView;
        TextView statusBadgeTextView;
        ImageButton menuButton;
        TextView routeNameTextView;
        TextView rejectionReasonLink;
        IncludeInfoChipBinding distanceChipBinding;
        IncludeInfoChipBinding difficultyChipBinding;

        public RouteViewHolder(@NonNull View itemView) {
            super(itemView);
            staticMapImageView = itemView.findViewById(R.id.routeStaticMapImageView);
            statusBadgeTextView = itemView.findViewById(R.id.statusBadgeTextView);
            menuButton = itemView.findViewById(R.id.menuButton);
            routeNameTextView = itemView.findViewById(R.id.routeNameTextView);
            rejectionReasonLink = itemView.findViewById(R.id.rejectionReasonLink);
            distanceChipBinding = IncludeInfoChipBinding.bind(itemView.findViewById(R.id.distanceChip));
            difficultyChipBinding = IncludeInfoChipBinding.bind(itemView.findViewById(R.id.difficultyChip));
        }

        public void bind(RouteModel route, Context context, SettingsService settingsService, OnRouteActionListener actionListener) {
            // Carica immagine mappa
            String mapUrl = route.getStaticMapUrl();
            if (mapUrl == null || mapUrl.isEmpty()) {
                staticMapImageView.setImageResource(R.drawable.ic_map);
                staticMapImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                staticMapImageView.setBackgroundColor(ContextCompat.getColor(context, R.color.borderColor));
            } else {
                Glide.with(context)
                        .load(mapUrl)
                        .centerCrop()
                        .placeholder(R.color.borderColor)
                        .error(R.drawable.ic_map)
                        .into(staticMapImageView);
            }

            // Imposta nome
            routeNameTextView.setText(route.getNome());

            // Imposta Info Chips
            setupChip(context, distanceChipBinding, R.drawable.ic_linear_scale, settingsService.formatDistance(route.getDistanzaKm()), R.color.subtleTextColor);
            setupChip(context, difficultyChipBinding, R.drawable.ic_speed, route.getDifficulty(), getDifficultyColorRes(context, route.getDifficulty()));

            // Imposta Status Badge
            setupStatusBadge(context, route.getStatus());

            // Imposta link Ragione Rifiuto
            if ("rejected".equals(route.getStatus()) && !TextUtils.isEmpty(route.getRejectionReason())) {
                rejectionReasonLink.setVisibility(View.VISIBLE);
                rejectionReasonLink.setOnClickListener(v -> {
                    actionListener.onShowRejectionReason(route.getRejectionReason());
                });
            } else {
                rejectionReasonLink.setVisibility(View.GONE);
            }

            // Imposta click sul menu "Elimina"
            menuButton.setOnClickListener(v -> showPopupMenu(v, route, actionListener));

            // Imposta click sull'intera card (solo se approvato)
            if ("approved".equals(route.getStatus())) {
                itemView.setOnClickListener(v -> {
                    // TODO: Navigare a RouteDetailActivity
                    // Intent intent = new Intent(context, RouteDetailActivity.class);
                    // intent.putExtra(RouteDetailActivity.EXTRA_ROUTE_ID, route.getId());
                    // context.startActivity(intent);
                    Toast.makeText(context, "Open details for " + route.getNome(), Toast.LENGTH_SHORT).show();
                });
                itemView.setClickable(true);
            } else {
                itemView.setClickable(false);
            }
        }

        // --- Metodi helper (invariati) ---
        private void setupChip(Context context, IncludeInfoChipBinding chipBinding, int iconRes, String text, int colorRes) {
            int color = ContextCompat.getColor(context, colorRes);
            chipBinding.chipIcon.setImageResource(iconRes);
            chipBinding.chipText.setText(text);
            ImageViewCompat.setImageTintList(chipBinding.chipIcon, ColorStateList.valueOf(color));
            chipBinding.chipText.setTextColor(color);
            GradientDrawable chipBackground = (GradientDrawable) ContextCompat.getDrawable(context, R.drawable.chip_background_grey).mutate();
            chipBackground.setColor(color & 0x1AFFFFFF);
            chipBinding.getRoot().setBackground(chipBackground);
        }

        private void setupStatusBadge(Context context, String status) {
            int backgroundColorRes;
            int textColorRes;
            String text;

            switch (status) {
                case "pending":
                    backgroundColorRes = R.color.warning_light;
                    textColorRes = R.color.warning_dark;
                    text = context.getString(R.string.my_routes_status_pending);
                    break;
                case "approved":
                    backgroundColorRes = R.color.success_light;
                    textColorRes = R.color.success_dark;
                    text = context.getString(R.string.my_routes_status_approved);
                    break;
                case "rejected":
                    backgroundColorRes = R.color.danger_light;
                    textColorRes = R.color.danger_dark;
                    text = context.getString(R.string.my_routes_status_rejected);
                    break;
                default:
                    backgroundColorRes = R.color.borderColor;
                    textColorRes = R.color.subtleTextColor;
                    text = context.getString(R.string.my_routes_status_unknown);
            }

            statusBadgeTextView.setText(text);
            statusBadgeTextView.setTextColor(ContextCompat.getColor(context, textColorRes));
            GradientDrawable badgeBg = (GradientDrawable) ContextCompat.getDrawable(context, R.drawable.status_badge_background).mutate();
            badgeBg.setColor(ContextCompat.getColor(context, backgroundColorRes));
            statusBadgeTextView.setBackground(badgeBg);
        }

        private int getDifficultyColorRes(Context context, String difficulty) {
            if (difficulty == null) return R.color.subtleTextColor;
            switch (difficulty.toLowerCase()) {
                case "easy": return R.color.successColor;
                case "medium": return R.color.warningColor;
                case "hard": return R.color.dangerColor;
                default: return R.color.subtleTextColor;
            }
        }

        private void showPopupMenu(View v, RouteModel route, OnRouteActionListener actionListener) {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.getMenuInflater().inflate(R.menu.my_route_item_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_delete_route) {
                    new AlertDialog.Builder(v.getContext())
                            .setTitle(R.string.my_routes_delete_dialog_title)
                            .setMessage(R.string.my_routes_delete_dialog_content)
                            .setPositiveButton(R.string.my_routes_delete_dialog_confirm, (dialog, which) -> {
                                actionListener.onRouteDelete(route);
                            })
                            .setNegativeButton(R.string.my_routes_delete_dialog_cancel, null)
                            .show();
                    return true;
                }
                return false;
            });
            popup.show();
        }
    }
}