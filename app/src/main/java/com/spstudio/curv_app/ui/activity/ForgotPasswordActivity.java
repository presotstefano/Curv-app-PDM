package com.spstudio.curv_app.ui.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.ActivityForgotPasswordBinding;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPasswordActivity";

    private ActivityForgotPasswordBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();

        // Setup texts from resources
        binding.appBarTitle.setText(R.string.forgot_password_title);
        binding.headerTextView.setText(R.string.forgot_password_header);
        binding.subheaderTextView.setText(R.string.forgot_password_subheader);
        binding.emailInputLayout.setHint(R.string.email_hint); // Usa email_hint generico
        binding.sendLinkButton.setText(R.string.forgot_password_button);

        binding.backButton.setOnClickListener(v -> finish());
        binding.sendLinkButton.setOnClickListener(v -> sendResetLink());
    }

    private void sendResetLink() {
        String email = binding.emailEditText.getText().toString().trim();

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.setError(getString(R.string.error_invalid_email));
            return;
        } else {
            binding.emailInputLayout.setError(null);
        }

        setLoading(true);
        hideMessage();

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Password reset email sent.");
                        showMessage(getString(R.string.forgot_password_success_message), false);
                        binding.sendLinkButton.setEnabled(false); // Disabilita dopo successo
                    } else {
                        Log.w(TAG, "sendPasswordResetEmail:failure", task.getException());
                        showMessage(task.getException() != null ? task.getException().getMessage() : "An unknown error occurred.", true);
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.sendLinkButton.setEnabled(!isLoading);
        binding.emailEditText.setEnabled(!isLoading);
        binding.backButton.setEnabled(!isLoading);
    }

    private void showMessage(String message, boolean isError) {
        binding.messageTextView.setText(message);
        if (isError) {
            binding.messageTextView.setBackgroundResource(R.drawable.error_background);
            binding.messageTextView.setTextColor(ContextCompat.getColor(this, R.color.dangerColor));
        } else {
            binding.messageTextView.setBackgroundResource(R.drawable.success_background);
            binding.messageTextView.setTextColor(ContextCompat.getColor(this, R.color.successColor));
        }
        binding.messageTextView.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        binding.messageTextView.setVisibility(View.GONE);
    }
}