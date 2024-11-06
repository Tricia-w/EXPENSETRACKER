package assignment.a202sg1tester;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";

    private EditText emailEditText, passwordEditText, confirmPasswordEditText;
    private Button signUpButton;
    private TextView backTextView;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signup_page);

        // Initialize the views
        emailEditText = findViewById(R.id.signup_email_edit_text);
        passwordEditText = findViewById(R.id.signup_password_edit_text);
        confirmPasswordEditText = findViewById(R.id.signup_confirm_password_edit_text);
        signUpButton = findViewById(R.id.signup_button);
        backTextView = findViewById(R.id.back_text_view);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Underline the "Already have an account? Log in" text
        backTextView.setText(Html.fromHtml("<u>Already have an account? Log in</u>"));

        // Set up the sign-up button click event
        signUpButton.setOnClickListener(v -> {
            if (isNetworkConnected()) {
                String email = emailEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();
                String confirmPassword = confirmPasswordEditText.getText().toString().trim();

                if (!password.equals(confirmPassword)) {
                    Toast.makeText(SignUpActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                } else {
                    checkIfEmailExists(email, password);
                }
            } else {
                Toast.makeText(SignUpActivity.this, "No network connection available", Toast.LENGTH_SHORT).show();
            }
        });

        // Set up the "Already have an account? Log in" text click event
        backTextView.setOnClickListener(v -> {
            Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void checkIfEmailExists(String email, String password) {
        db.collection("users").whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(SignUpActivity.this, "Email already in use", Toast.LENGTH_SHORT).show();
                    } else {
                        signUpWithFirebase(email, password);
                    }
                })
                .addOnFailureListener(e -> {
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    Log.e(TAG, "Error checking email: " + errorMessage);
                    Toast.makeText(SignUpActivity.this, "Error checking email: " + errorMessage, Toast.LENGTH_SHORT).show();
                });
    }

    private void signUpWithFirebase(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            sendVerificationEmail(user);
                        }
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Log.e(TAG, "Authentication failed: " + errorMessage);
                        Toast.makeText(SignUpActivity.this, "Authentication failed: " + errorMessage,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(SignUpActivity.this, "Verification email sent. Please check your inbox.", Toast.LENGTH_SHORT).show();
                        saveUserDataToFirestore(user.getUid(), user.getEmail());
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Log.e(TAG, "Failed to send verification email: " + errorMessage);
                        Toast.makeText(SignUpActivity.this, "Failed to send verification email: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserDataToFirestore(String userId, String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("email", email);

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "User registered successfully with email: " + email);
                    Toast.makeText(SignUpActivity.this, "User Registered Successfully. Please verify your email before logging in.", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    Log.e(TAG, "Error saving user: " + errorMessage);
                    Toast.makeText(SignUpActivity.this, "Error saving user: " + errorMessage, Toast.LENGTH_SHORT).show();
                });
    }
}
