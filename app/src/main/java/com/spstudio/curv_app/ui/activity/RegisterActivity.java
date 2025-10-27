package com.spstudio.curv_app.ui.activity;

import android.content.Intent;
import android.graphics.Color; // Import Color
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat; // Import ContextCompat

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.ActivityRegisterBinding;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.registerButton.setOnClickListener(v -> attemptRegistration());
        binding.loginTextView.setOnClickListener(v -> navigateToLogin());

        // Update titles/prompts using string resources
        binding.titleTextView.setText(R.string.register_title);
        binding.subtitleTextView.setText(R.string.register_subtitle);
        binding.togglePromptTextView.setText(R.string.already_account_prompt);
        binding.loginTextView.setText(R.string.login_button_text); // Text on the link

        setupTermsAndPolicyLinks();
    }

    private void attemptRegistration() {
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        String confirmPassword = binding.confirmPasswordEditText.getText().toString().trim();
        boolean termsAccepted = binding.termsCheckBox.isChecked();

        // Validazione Input
        boolean valid = true;
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.setError(getString(R.string.error_invalid_email));
            valid = false;
        } else {
            binding.emailInputLayout.setError(null);
        }

        if (password.isEmpty() || password.length() < 6) {
            binding.passwordInputLayout.setError(getString(R.string.error_password_length));
            valid = false;
        } else {
            binding.passwordInputLayout.setError(null);
        }

        if (!password.equals(confirmPassword)) {
            binding.confirmPasswordInputLayout.setError(getString(R.string.error_password_mismatch));
            valid = false;
        } else {
            binding.confirmPasswordInputLayout.setError(null);
        }

        if (!termsAccepted) {
            showError(getString(R.string.error_terms_unchecked));
            valid = false;
        } else {
            binding.errorTextView.setVisibility(View.GONE);
        }

        if (!valid) return;

        setLoading(true);
        binding.errorTextView.setVisibility(View.GONE);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            createFirestoreUserDocument(user, email);
                            user.sendEmailVerification()
                                    .addOnCompleteListener(sendTask -> {
                                        if(sendTask.isSuccessful()){
                                            Log.d(TAG, "Verification email sent.");
                                            navigateToEmailVerification();
                                        } else {
                                            Log.e(TAG, "sendEmailVerification", sendTask.getException());
                                            setLoading(false);
                                            showError(getString(R.string.verification_failed_toast));
                                        }
                                    });
                        } else {
                            setLoading(false);
                            showError("Failed to create user.");
                        }
                    } else {
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        setLoading(false);
                        showError(task.getException() != null ? task.getException().getMessage() : "Registration failed.");
                    }
                });
    }

    private void createFirestoreUserDocument(FirebaseUser user, String email) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", user.getUid());
        userData.put("email", email);
        userData.put("dataCreazione", com.google.firebase.Timestamp.now());
        userData.put("isProfileComplete", false);
        userData.put("followerCount", 0);
        userData.put("followingCount", 0);
        userData.put("notificationsEnabled", true);

        db.collection("utenti").document(user.getUid())
                .set(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User document successfully written!"))
                .addOnFailureListener(e -> Log.w(TAG, "Error writing user document", e));
    }

    private void navigateToLogin() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void navigateToEmailVerification() {
        Intent intent = new Intent(RegisterActivity.this, EmailVerificationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.registerButton.setEnabled(!isLoading);
        binding.emailEditText.setEnabled(!isLoading);
        binding.passwordEditText.setEnabled(!isLoading);
        binding.confirmPasswordEditText.setEnabled(!isLoading);
        binding.termsCheckBox.setEnabled(!isLoading);
        binding.termsTextView.setEnabled(!isLoading);
        binding.loginTextView.setEnabled(!isLoading);
        binding.togglePromptTextView.setEnabled(!isLoading);
    }

    private void showError(String message) {
        binding.errorTextView.setText(message != null ? message : "An unknown error occurred.");
        binding.errorTextView.setVisibility(View.VISIBLE);
    }

    private void setupTermsAndPolicyLinks() {
        String fullText = getString(R.string.terms_agreement); // Usa la stringa dalle risorse
        SpannableString ss = new SpannableString(fullText);

        ClickableSpan termsSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // Intent intent = new Intent(RegisterActivity.this, TermsOfServiceActivity.class);
                // startActivity(intent);
                Toast.makeText(RegisterActivity.this, "Terms Clicked (Implement Navigation)", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
                // Usa ContextCompat per ottenere i colori in modo sicuro
                ds.setColor(ContextCompat.getColor(RegisterActivity.this, R.color.primaryColor));
            }
        };

        ClickableSpan policySpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // Intent intent = new Intent(RegisterActivity.this, PrivacyPolicyActivity.class);
                // startActivity(intent);
                Toast.makeText(RegisterActivity.this, "Policy Clicked (Implement Navigation)", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
                ds.setColor(ContextCompat.getColor(RegisterActivity.this, R.color.primaryColor));
            }
        };

        // Usa parole chiave fisse o estrai da R.string se preferisci
        String termsKeyword = "Terms of Service";
        String policyKeyword = "Privacy Policy";
        int termsStart = fullText.indexOf(termsKeyword);
        int termsEnd = termsStart + termsKeyword.length();
        int policyStart = fullText.indexOf(policyKeyword);
        int policyEnd = policyStart + policyKeyword.length();

        if (termsStart != -1) {
            ss.setSpan(termsSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (policyStart != -1) {
            ss.setSpan(policySpan, policyStart, policyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        binding.termsTextView.setText(ss);
        binding.termsTextView.setMovementMethod(LinkMovementMethod.getInstance());
        binding.termsTextView.setHighlightColor(Color.TRANSPARENT);
    }
}