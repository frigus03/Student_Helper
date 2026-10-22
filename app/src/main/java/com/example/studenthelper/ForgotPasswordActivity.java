package com.example.studenthelper;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private MaterialButton btnReset;
    private ProgressBar progressBar;
    private TextView tvLogin;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.etEmail);
        btnReset = findViewById(R.id.btnReset);
        progressBar = findViewById(R.id.progressBar);
        tvLogin = findViewById(R.id.tvLogin);

        btnReset.setOnClickListener(v -> resetPassword());
        tvLogin.setOnClickListener(v -> finish());
    }

    private void resetPassword() {
        String email = etEmail.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please provide a valid email");
            etEmail.requestFocus();
            return;
        }

        setInProgress(true);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    setInProgress(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(ForgotPasswordActivity.this, "Check your email to reset your password", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(ForgotPasswordActivity.this, "Try again! Something wrong happened: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setInProgress(boolean inProgress) {
        if (inProgress) {
            progressBar.setVisibility(View.VISIBLE);
            btnReset.setVisibility(View.INVISIBLE);
        } else {
            progressBar.setVisibility(View.GONE);
            btnReset.setVisibility(View.VISIBLE);
        }
    }
}
