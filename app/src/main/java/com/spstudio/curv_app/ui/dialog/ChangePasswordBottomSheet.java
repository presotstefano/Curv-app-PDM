package com.spstudio.curv_app.ui.dialog;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.BottomSheetChangePasswordBinding; // Importa binding

public class ChangePasswordBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ChangePasswordSheet";

    private BottomSheetChangePasswordBinding binding;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private boolean isLoading = false;

    // Factory method (opzionale, non servono argomenti qui)
    public static ChangePasswordBottomSheet newInstance() {
        return new ChangePasswordBottomSheet();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetChangePasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (currentUser == null || currentUser.getEmail() == null) {
            showError("User not logged in or email missing.");
            binding.updatePasswordButton.setEnabled(false);
            return;
        }

        binding.updatePasswordButton.setOnClickListener(v -> attemptPasswordChange());
    }

    private void attemptPasswordChange() {
        if (isLoading) return;

        String currentPassword = binding.currentPasswordEditText.getText().toString();
        String newPassword = binding.newPasswordEditText.getText().toString();
        String confirmPassword = binding.confirmPasswordEditText.getText().toString();

        // Validazione Input
        boolean valid = true;
        if (TextUtils.isEmpty(currentPassword)) {
            binding.currentPasswordInputLayout.setError("Current password is required");
            valid = false;
        } else {
            binding.currentPasswordInputLayout.setError(null);
        }
        if (TextUtils.isEmpty(newPassword) || newPassword.length() < 6) {
            binding.newPasswordInputLayout.setError(getString(R.string.error_password_length));
            valid = false;
        } else {
            binding.newPasswordInputLayout.setError(null);
        }
        if (!newPassword.equals(confirmPassword)) {
            binding.confirmPasswordInputLayout.setError(getString(R.string.error_password_mismatch));
            valid = false;
        } else {
            binding.confirmPasswordInputLayout.setError(null);
        }

        if (!valid) return;

        setLoading(true);
        hideError();

        // 1. Ri-autentica l'utente con la password corrente
        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);
        currentUser.reauthenticate(credential)
                .addOnCompleteListener(reauthTask -> {
                    if (reauthTask.isSuccessful()) {
                        Log.d(TAG, "User re-authenticated successfully.");
                        // 2. Se la ri-autenticazione ha successo, aggiorna la password
                        currentUser.updatePassword(newPassword)
                                .addOnCompleteListener(updateTask -> {
                                    if (updateTask.isSuccessful()) {
                                        Log.d(TAG, "User password updated successfully.");
                                        Toast.makeText(getContext(), R.string.change_password_success, Toast.LENGTH_SHORT).show();
                                        setLoading(false);
                                        dismiss(); // Chiudi il BottomSheet
                                    } else {
                                        Log.w(TAG, "Error updating password.", updateTask.getException());
                                        // Mostra errore specifico (es. password debole)
                                        String errorMessage = updateTask.getException() != null ?
                                                updateTask.getException().getMessage() : "Unknown error";
                                        showError(getString(R.string.change_password_error, errorMessage));
                                        setLoading(false);
                                    }
                                });
                    } else {
                        Log.w(TAG, "Re-authentication failed.", reauthTask.getException());
                        // Controlla se l'errore è dovuto a password corrente errata
                        if (reauthTask.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                            binding.currentPasswordInputLayout.setError("Incorrect current password");
                        } else {
                            // Mostra errore generico
                            String errorMessage = reauthTask.getException() != null ?
                                    reauthTask.getException().getMessage() : "Re-authentication failed";
                            showError(getString(R.string.change_password_error, errorMessage));
                        }
                        setLoading(false);
                    }
                });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        binding.progressBarPassword.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.updatePasswordButton.setEnabled(!loading);
        binding.currentPasswordEditText.setEnabled(!loading);
        binding.newPasswordEditText.setEnabled(!loading);
        binding.confirmPasswordEditText.setEnabled(!loading);
    }

    private void showError(String message) {
        binding.errorTextView.setText(message);
        binding.errorTextView.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        binding.errorTextView.setVisibility(View.GONE);
        // Rimuovi errori dai campi di input
        binding.currentPasswordInputLayout.setError(null);
        binding.newPasswordInputLayout.setError(null);
        binding.confirmPasswordInputLayout.setError(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Pulisci binding
    }
}