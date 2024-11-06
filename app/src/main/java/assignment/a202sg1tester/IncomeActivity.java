package assignment.a202sg1tester;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IncomeActivity extends AppCompatActivity {

    private EditText incomeSourceEditText, incomeAmountEditText;
    private Button addIncomeButton, backToDashboardButton;
    private ListView incomeListView;
    private TextView totalIncomeTextView;

    private ArrayList<String> incomeList;
    private ArrayList<String> incomeIds;
    private ArrayAdapter<String> incomeAdapter;
    private double totalIncome = 0.0;
    private int editingPosition = -1;
    private String editingDocumentId = null;

    // Firebase variables
    private FirebaseFirestore db;
    private CollectionReference incomeCollection;
    private FirebaseAuth auth;
    private String userId;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm"); // Date format for displaying timestamp

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.income_page);

        // Initialize views
        incomeSourceEditText = findViewById(R.id.income_source_edit_text);
        incomeAmountEditText = findViewById(R.id.income_amount_edit_text);
        addIncomeButton = findViewById(R.id.add_income_button);
        backToDashboardButton = findViewById(R.id.back_button);
        incomeListView = findViewById(R.id.income_list_view);
        totalIncomeTextView = findViewById(R.id.total_income_text_view);

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        userId = auth.getCurrentUser().getUid();
        incomeCollection = db.collection("users").document(userId).collection("income");

        // Initialize the income list and adapter
        incomeList = new ArrayList<>();
        incomeIds = new ArrayList<>();
        incomeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, incomeList);
        incomeListView.setAdapter(incomeAdapter);

        // Load user-specific income data
        loadIncomeData();

        // Set up "Add Income" button click event
        addIncomeButton.setOnClickListener(v -> {
            if (editingPosition == -1) {
                addIncome();
            } else {
                updateIncome();
            }
        });

        // Set up "Back to Dashboard" button click event
        backToDashboardButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(Constants.EXTRA_UPDATED_INCOMES, incomeList);
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        // Set up item click listener for edit/delete options
        incomeListView.setOnItemClickListener((parent, view, position, id) -> showOptionsDialog(position));
    }

    private void loadIncomeData() {
        incomeCollection.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    incomeList.clear();
                    incomeIds.clear();
                    totalIncome = 0.0;

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String source = document.getString("source");
                        double amount = document.getDouble("amount");
                        Timestamp timestamp = document.getTimestamp("timestamp");
                        String documentId = document.getId();

                        String dateText = timestamp != null ? dateFormat.format(timestamp.toDate()) : "No date";
                        String incomeEntry = source + ": RM" + amount + " (" + dateText + ")";

                        incomeList.add(incomeEntry);
                        incomeIds.add(documentId);

                        totalIncome += amount;
                    }

                    totalIncomeTextView.setText("Total Income: RM" + totalIncome);
                    incomeAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(IncomeActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show());
    }

    private void addIncome() {
        String source = incomeSourceEditText.getText().toString();
        String amountText = incomeAmountEditText.getText().toString();

        if (TextUtils.isEmpty(source)) {
            incomeSourceEditText.setError("Source is required");
            return;
        }

        if (TextUtils.isEmpty(amountText)) {
            incomeAmountEditText.setError("Amount is required");
            return;
        }

        try {
            double amount = Double.parseDouble(amountText);

            Map<String, Object> incomeData = new HashMap<>();
            incomeData.put("source", source);
            incomeData.put("amount", amount);
            incomeData.put("timestamp", Timestamp.now()); // Add current timestamp

            incomeCollection.add(incomeData)
                    .addOnSuccessListener(documentReference -> {
                        loadIncomeData(); // Reload data to update the UI and recalculate totals
                        incomeSourceEditText.setText("");
                        incomeAmountEditText.setText("");
                    })
                    .addOnFailureListener(e -> Toast.makeText(IncomeActivity.this, "Failed to add income", Toast.LENGTH_SHORT).show());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateIncome() {
        String source = incomeSourceEditText.getText().toString();
        String amountText = incomeAmountEditText.getText().toString();

        if (TextUtils.isEmpty(source)) {
            incomeSourceEditText.setError("Source is required");
            return;
        }

        if (TextUtils.isEmpty(amountText)) {
            incomeAmountEditText.setError("Amount is required");
            return;
        }

        try {
            double amount = Double.parseDouble(amountText);

            Map<String, Object> updatedIncomeData = new HashMap<>();
            updatedIncomeData.put("source", source);
            updatedIncomeData.put("amount", amount);
            updatedIncomeData.put("timestamp", Timestamp.now()); // Update timestamp to current time

            incomeCollection.document(editingDocumentId)
                    .set(updatedIncomeData)
                    .addOnSuccessListener(aVoid -> {
                        loadIncomeData();
                        incomeSourceEditText.setText("");
                        incomeAmountEditText.setText("");
                        editingPosition = -1;
                        addIncomeButton.setText("Add Income");
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update income", Toast.LENGTH_SHORT).show());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOptionsDialog(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Select Action")
                .setMessage("Would you like to edit or delete this income entry?")
                .setPositiveButton("Edit", (dialog, which) -> editIncome(position))
                .setNegativeButton("Delete", (dialog, which) -> deleteIncome(position))
                .show();
    }

    private void editIncome(int position) {
        String item = incomeList.get(position);
        String source = item.substring(0, item.indexOf(":"));
        String amountText = item.substring(item.indexOf("RM") + 2, item.indexOf(" ("));

        incomeSourceEditText.setText(source);
        incomeAmountEditText.setText(amountText);
        editingPosition = position;
        editingDocumentId = incomeIds.get(position); // Retrieve associated document ID
        addIncomeButton.setText("Update Income");
    }

    private void deleteIncome(int position) {
        String documentId = incomeIds.get(position);

        incomeCollection.document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    loadIncomeData();
                    Toast.makeText(this, "Income entry deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete income", Toast.LENGTH_SHORT).show());
    }
}
