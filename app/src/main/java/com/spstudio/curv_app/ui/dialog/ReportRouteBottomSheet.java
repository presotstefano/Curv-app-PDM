package com.spstudio.curv_app.ui.dialog;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.BottomSheetReportRouteBinding;

import java.util.HashMap;
import java.util.Map;

public class ReportRouteBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ReportRouteSheet";
    private static final String ARG_ROUTE_ID = "routeId";
    private static final String ARG_ROUTE_NAME = "routeName";
    private static final String ARG_CREATOR_UID = "creatorUid";

    private BottomSheetReportRouteBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String routeId, routeName, creatorUid;
    private boolean isLoading = false;

    public static ReportRouteBottomSheet newInstance(String routeId, String routeName, String creatorUid) {
        ReportRouteBottomSheet fragment = new ReportRouteBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ROUTE_ID, routeId);
        args.putString(ARG_ROUTE_NAME, routeName);
        args.putString(ARG_CREATOR_UID, creatorUid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            routeId = getArguments().getString(ARG_ROUTE_ID);
            routeName = getArguments().getString(ARG_ROUTE_NAME);
            creatorUid = getArguments().getString(ARG_CREATOR_UID);
        }
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetReportRouteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Mostra/nascondi il campo "commento" se "Other" è selezionato
        binding.reportReasonRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.reason_other) {
                binding.commentInputLayout.setVisibility(View.VISIBLE);
            } else {
                binding.commentInputLayout.setVisibility(View.GONE);
            }
        });

        binding.submitReportButton.setOnClickListener(v -> submitReport());
    }

    private void submitReport() {
        if (isLoading) return;
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "You must be logged in to report.", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedId = binding.reportReasonRadioGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Toast.makeText(getContext(), R.string.route_detail_report_reason_error, Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRadioButton = binding.reportReasonRadioGroup.findViewById(selectedId);
        String reason = selectedRadioButton.getText().toString(); // Usa il testo della stringa (già localizzato)
        String comment = binding.commentEditText.getText().toString().trim();

        if (selectedId == R.id.reason_other && TextUtils.isEmpty(comment)) {
            binding.commentInputLayout.setError("Please provide details for 'Other'.");
            return;
        } else {
            binding.commentInputLayout.setError(null);
        }

        setLoading(true);

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("routeId", routeId);
        reportData.put("routeName", routeName);
        reportData.put("creatorUid", creatorUid);
        reportData.put("reporterUid", user.getUid());
        reportData.put("reason", reason); // Salva la ragione localizzata
        reportData.put("comment", comment);
        reportData.put("timestamp", com.google.firebase.Timestamp.now());
        reportData.put("status", "pending"); // Stato iniziale

        db.collection("reports").add(reportData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Report submitted successfully.");
                    Toast.makeText(getContext(), R.string.route_detail_report_success, Toast.LENGTH_SHORT).show();
                    setLoading(false);
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error submitting report", e);
                    Toast.makeText(getContext(), getString(R.string.route_detail_report_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                    setLoading(false);
                });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        binding.progressBarReport.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.submitReportButton.setEnabled(!loading);
        binding.reportReasonRadioGroup.setEnabled(!loading);
        binding.commentEditText.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}