package com.spstudio.curv_app.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.ActivityLoginBinding;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();

        binding.loginButton.setOnClickListener(v -> attemptLogin());
        binding.forgotPasswordTextView.setOnClickListener(v -> navigateToForgotPassword());
        binding.registerTextView.setOnClickListener(v -> navigateToRegister());

        // Update titles/prompts using string resources
        binding.titleTextView.setText(R.string.login_welcome_back);
        binding.subtitleTextView.setText(R.string.login_subtitle);
        binding.togglePromptTextView.setText(R.string.no_account_prompt);
        binding.registerTextView.setText(R.string.register_button_text); // Text on the link

    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            navigateToMain();
        } else if (currentUser != null && !currentUser.isEmailVerified()) {
            navigateToEmailVerification();
        }
    }

    private void attemptLogin() {
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.setError(getString(R.string.error_invalid_email));
            return;
        } else {
            binding.emailInputLayout.setError(null);
        }

        if (password.isEmpty() || password.length() < 6) {
            binding.passwordInputLayout.setError(getString(R.string.error_password_length));
            return;
        } else {
            binding.passwordInputLayout.setError(null);
        }

        setLoading(true);
        binding.errorTextView.setVisibility(View.GONE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.isEmailVerified()) {
                            navigateToMain();
                        } else if (user != null && !user.isEmailVerified()) {
                            navigateToEmailVerification();
                        } else {
                            setLoading(false);
                            showError("Authentication failed."); // Keep generic error here
                        }
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        setLoading(false);
                        showError(task.getException() != null ? task.getException().getMessage() : "Authentication failed.");
                    }
                });
    }

    private void navigateToRegister() {
        Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
        startActivity(intent);
    }

    private void navigateToForgotPassword() {
        Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
        startActivity(intent);
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToEmailVerification() {
        Intent intent = new Intent(LoginActivity.this, EmailVerificationActivity.class);
        startActivity(intent);
        setLoading(false);
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.loginButton.setEnabled(!isLoading);
        binding.emailEditText.setEnabled(!isLoading);
        binding.passwordEditText.setEnabled(!isLoading);
        binding.forgotPasswordTextView.setEnabled(!isLoading);
        binding.registerTextView.setEnabled(!isLoading);
        binding.togglePromptTextView.setEnabled(!isLoading); // Disable prompt text too
    }

    private void showError(String message) {
        binding.errorTextView.setText(message != null ? message : "An unknown error occurred.");
        binding.errorTextView.setVisibility(View.VISIBLE);
    }
}