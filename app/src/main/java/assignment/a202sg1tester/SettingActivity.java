package assignment.a202sg1tester;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class SettingActivity extends AppCompatActivity {

    private EditText editUsername;
    private SwitchCompat switchDarkMode, switchNotifications;
    private Button editButton, logoutButton, backButton;

    private SharedPreferences sharedPreferences;
    private boolean isEditing = false;

    private FirebaseFirestore db;
    private String userId;

    private static final String TAG = "SettingActivity"; // Tag for logging

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set theme before setting the content view
        setThemeFromPreferences();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting_page);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        initializeViews();
        loadUserSettings();
        setUpListeners();
    }

    private void initializeViews() {
        editUsername = findViewById(R.id.editUsername);
        switchDarkMode = findViewById(R.id.switch_dark_mode);
        switchNotifications = findViewById(R.id.switch_notifications);
        editButton = findViewById(R.id.editButton);
        logoutButton = findViewById(R.id.logoutButton);
        backButton = findViewById(R.id.backButton);
    }

    private void setUpListeners() {
        // Set action listener for editing username
        editUsername.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveUsername();
                return true;
            }
            return false;
        });

        editButton.setOnClickListener(view -> {
            if (isEditing) {
                saveUsername();
            } else {
                enableEditing();
            }
        });

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            saveThemePreference(isChecked);
        });

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveNotificationPreference(isChecked);
            Toast.makeText(this, isChecked ? getString(R.string.notifications_enabled) : getString(R.string.notifications_disabled), Toast.LENGTH_SHORT).show();
        });

        logoutButton.setOnClickListener(view -> showLogoutConfirmationDialog());

        backButton.setOnClickListener(view -> {
            Intent intent = new Intent(SettingActivity.this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish(); // Optional
        });
    }

    private void setThemeFromPreferences() {
        sharedPreferences = getSharedPreferences("UserSettings", MODE_PRIVATE);
        boolean isDarkMode = sharedPreferences.getBoolean("isDarkMode", false);
        AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void loadUserSettings() {
        sharedPreferences = getSharedPreferences("UserSettings", MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString("username", getString(R.string.default_username));
        boolean isDarkMode = sharedPreferences.getBoolean("isDarkMode", false);
        boolean receiveNotifications = sharedPreferences.getBoolean("receiveNotifications", true);

        editUsername.setText(savedUsername);
        switchDarkMode.setChecked(isDarkMode);
        switchNotifications.setChecked(receiveNotifications);

        loadUsernameFromFirebase();
    }

    private void loadUsernameFromFirebase() {
        DocumentReference userDoc = db.collection("users").document(userId);
        userDoc.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("username")) {
                        String firebaseUsername = documentSnapshot.getString("username");
                        editUsername.setText(firebaseUsername);

                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("username", firebaseUsername);
                        editor.apply();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load username from Firebase", e);
                    Toast.makeText(this, "Failed to load username. Check your network connection.", Toast.LENGTH_SHORT).show();
                });
    }

    private void enableEditing() {
        editUsername.setEnabled(true);
        editUsername.requestFocus();
        editButton.setText(R.string.save);
        isEditing = true;
    }

    private void saveUsername() {
        String newUsername = editUsername.getText().toString().trim();
        if (newUsername.isEmpty()) {
            Toast.makeText(this, R.string.username_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Username Change")
                .setMessage("Are you sure you want to change your username to: " + newUsername + "?")
                .setPositiveButton("Yes", (dialog, which) -> saveUsernameToFirebase(newUsername))
                .setNegativeButton("No", null)
                .show();
    }

    private void saveUsernameToFirebase(String newUsername) {
        editUsername.setEnabled(false);
        editButton.setText(R.string.edit);
        isEditing = false;

        // Save to SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("username", newUsername);
        if (!editor.commit()) {
            Log.e(TAG, "Failed to save username to SharedPreferences");
            Toast.makeText(this, "Failed to save locally. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save to Firebase
        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("username", newUsername);
        db.collection("users").document(userId)
                .set(userUpdates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Toast.makeText(this, R.string.username_updated, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update username in Firebase", e);
                    Toast.makeText(this, "Failed to update username on the server. Please try again later.", Toast.LENGTH_SHORT).show();
                });
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Clear login status in SharedPreferences
                    SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean("isLoggedIn", false); // Clear login state
                    editor.apply();

                    // Redirect to LoginActivity and clear the activity stack
                    Intent intent = new Intent(SettingActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("No", null)
                .show();
    }


    private void saveThemePreference(boolean isDarkMode) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isDarkMode", isDarkMode);
        editor.apply();
    }

    private void saveNotificationPreference(boolean receiveNotifications) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("receiveNotifications", receiveNotifications);
        editor.apply();
    }
}