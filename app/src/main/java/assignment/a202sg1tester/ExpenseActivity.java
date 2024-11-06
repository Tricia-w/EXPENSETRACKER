package assignment.a202sg1tester;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.widget.DatePicker;
import android.widget.TimePicker;

public class ExpenseActivity extends AppCompatActivity {

    private EditText expenseDescriptionEditText, expenseAmountEditText;
    private Button addExpenseButton, backToDashboardButton, addBudgetButton;
    private ListView expensesListView;
    private TextView totalExpensesTextView, budgetTextView;

    private ArrayList<String> expensesList;
    private ArrayList<String> expenseIds;
    private ArrayAdapter<String> expensesAdapter;
    private double totalExpenses = 0.0;
    private int editingPosition = -1;

    private static final String PREFS_NAME = "BudgetPrefs";
    private static final String KEY_BUDGET = "monthly_budget";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.expense_page);

        // Initialize Firebase Authentication and Firestore
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
        }

        db = FirebaseFirestore.getInstance();

        // Initialize views
        initializeViews();

        // Load budget and expenses from Firestore
        loadBudget();
        loadExpenses();

        // Set up button click listeners
        setButtonListeners();
    }

    private void initializeViews() {
        expenseDescriptionEditText = findViewById(R.id.expense_description_edit_text);
        expenseAmountEditText = findViewById(R.id.expense_amount_edit_text);
        addExpenseButton = findViewById(R.id.add_expense_button);
        backToDashboardButton = findViewById(R.id.back_button);
        addBudgetButton = findViewById(R.id.add_budget_button);
        expensesListView = findViewById(R.id.expenses_list_view);
        totalExpensesTextView = findViewById(R.id.total_expenses_text_view);
        budgetTextView = findViewById(R.id.budget_text_view);


        expensesList = new ArrayList<>();
        expenseIds = new ArrayList<>();
        expensesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, expensesList);
        expensesListView.setAdapter(expensesAdapter);
    }

    private void setButtonListeners() {
        addExpenseButton.setOnClickListener(v -> {
            if (editingPosition == -1) {
                addExpense();
            } else {
                updateExpense();
            }
        });

        backToDashboardButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("updated_expenses", expensesList);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            double budget = prefs.getFloat(KEY_BUDGET, 0.0f);
            resultIntent.putExtra("monthly_budget", budget);
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        addBudgetButton.setOnClickListener(v -> showAddBudgetDialog());

        expensesListView.setOnItemClickListener((parent, view, position, id) -> showOptionsDialog(position));

        expenseDescriptionEditText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                expenseAmountEditText.requestFocus();
                return true;
            }
            return false;
        });

        expenseAmountEditText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                addExpense();
                return true;
            }
            return false;
        });
    }

    private void loadBudget() {
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        int currentMonth = calendar.get(Calendar.MONTH);

        // Set start date to the first day of the month
        calendar.set(currentYear, currentMonth, 1, 0, 0, 0);
        Timestamp startOfMonth = new Timestamp(calendar.getTime());

        // Set end date to the last day of the month
        calendar.set(currentYear, currentMonth, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59);
        Timestamp endOfMonth = new Timestamp(calendar.getTime());

        db.collection("users").document(userId).collection("budget")
                .whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .whereLessThanOrEqualTo("timestamp", endOfMonth)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                        double budget = document.getDouble("budget");
                        budgetTextView.setText("Monthly Budget: RM " + String.format("%.2f", budget));
                    } else {
                        budgetTextView.setText("Monthly Budget: RM 0.00");
                    }
                })
                .addOnFailureListener(e -> showToast("Failed to load budget"));
    }

    private void loadExpenses() {
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        int currentMonth = calendar.get(Calendar.MONTH);

        // Set the start date to the first day of the month
        calendar.set(currentYear, currentMonth, 1, 0, 0, 0);
        Timestamp startOfMonth = new Timestamp(calendar.getTime());

        // Set the end date to the last day of the month
        calendar.set(currentYear, currentMonth, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59);
        Timestamp endOfMonth = new Timestamp(calendar.getTime());

        db.collection("users").document(userId).collection("expenses")
                .whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .whereLessThanOrEqualTo("timestamp", endOfMonth)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> updateExpensesList(queryDocumentSnapshots))
                .addOnFailureListener(e -> showToast("Failed to load expenses"));
    }

    private void updateExpensesList(QuerySnapshot queryDocumentSnapshots) {
        expensesList.clear();
        expenseIds.clear();
        totalExpenses = 0.0;

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm"); // Format for date and time

        for (DocumentSnapshot document : queryDocumentSnapshots) {
            String description = document.getString("description");
            double amount = document.getDouble("amount");
            Timestamp timestamp = document.getTimestamp("timestamp");

            String dateText = timestamp != null ? dateFormat.format(timestamp.toDate()) : "No date";
            String expenseEntry = description + ": RM" + String.format("%.2f", amount) + " (" + dateText + ")";

            expensesList.add(expenseEntry);
            expenseIds.add(document.getId());
            totalExpenses += amount;
        }

        expensesAdapter.notifyDataSetChanged();
        totalExpensesTextView.setText("Total Expenses: RM" + String.format("%.2f", totalExpenses));
    }

    private void addExpense() {
        String description = expenseDescriptionEditText.getText().toString().trim();
        String amountText = expenseAmountEditText.getText().toString().trim();

        if (TextUtils.isEmpty(description) || TextUtils.isEmpty(amountText)) {
            showToast("Please fill out both fields");
            return;
        }

        if (!isValidDouble(amountText)) {
            showToast("Enter a valid amount (e.g., 100.50)");
            return;
        }

        double amount = Double.parseDouble(amountText);
        addExpenseToFirestore(description, amount);
    }

    private void addExpenseToFirestore(String description, double amount) {
        String expenseEntry = description + ": RM" + String.format("%.2f", amount);
        expensesList.add(expenseEntry);
        expensesAdapter.notifyDataSetChanged();

        totalExpenses += amount;
        totalExpensesTextView.setText("Total Expenses: RM" + String.format("%.2f", totalExpenses));

        expenseDescriptionEditText.setText("");
        expenseAmountEditText.setText("");

        // Create the expense map with description, amount, and timestamp
        Map<String, Object> expense = new HashMap<>();
        expense.put("description", description);
        expense.put("amount", amount);
        expense.put("timestamp", Timestamp.now()); // Save the current time

        db.collection("users").document(userId).collection("expenses")
                .add(expense)
                .addOnSuccessListener(documentReference -> {
                    expenseIds.add(documentReference.getId());
                    showToast("Expense added to database");
                })
                .addOnFailureListener(e -> showToast("Failed to add expense"));
    }

    private void updateExpenseInFirestore(String description, double amount) {
        String item = expensesList.get(editingPosition);
        String oldAmountText = item.substring(item.indexOf("RM") + 2);
        double oldAmount = Double.parseDouble(oldAmountText.split(" ")[0]); // Extract only the amount

        totalExpenses = totalExpenses - oldAmount + amount;
        totalExpensesTextView.setText("Total Expenses: RM" + String.format("%.2f", totalExpenses));

        String updatedEntry = description + ": RM" + String.format("%.2f", amount);
        expensesList.set(editingPosition, updatedEntry);
        expensesAdapter.notifyDataSetChanged();

        String documentId = expenseIds.get(editingPosition);

        Map<String, Object> updatedExpense = new HashMap<>();
        updatedExpense.put("description", description);
        updatedExpense.put("amount", amount);
        updatedExpense.put("timestamp", Timestamp.now()); // Update the timestamp

        db.collection("users").document(userId).collection("expenses").document(documentId)
                .set(updatedExpense)
                .addOnSuccessListener(aVoid -> {
                    showToast("Expense updated successfully");
                    resetEditingMode();
                })
                .addOnFailureListener(e -> showToast("Failed to update expense"));
    }


    private void updateExpense() {
        String description = expenseDescriptionEditText.getText().toString().trim();
        String amountText = expenseAmountEditText.getText().toString().trim();

        if (TextUtils.isEmpty(description) || TextUtils.isEmpty(amountText)) {
            showToast("Please fill out both fields");
            return;
        }

        if (!isValidDouble(amountText)) {
            showToast("Enter a valid amount (e.g., 100.50)");
            return;
        }

        double amount = Double.parseDouble(amountText);
        updateExpenseInFirestore(description, amount);
    }


    private void deleteExpense(int position) {
        String documentId = expenseIds.get(position);

        db.collection("users").document(userId).collection("expenses").document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    String item = expensesList.get(position);
                    String amountText = item.substring(item.indexOf("RM") + 2);
                    double amount = Double.parseDouble(amountText.split(" ")[0]); // Extract only the amount

                    totalExpenses -= amount;
                    totalExpensesTextView.setText("Total Expenses: RM" + String.format("%.2f", totalExpenses));

                    expensesList.remove(position);
                    expenseIds.remove(position);
                    expensesAdapter.notifyDataSetChanged();

                    showToast("Expense deleted successfully");
                })
                .addOnFailureListener(e -> showToast("Failed to delete expense"));
    }

    private void resetEditingMode() {
        addExpenseButton.setText("Add Expense");
        editingPosition = -1;
        expenseDescriptionEditText.setText("");
        expenseAmountEditText.setText("");
    }

    private void showOptionsDialog(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose an option")
                .setItems(new String[]{"Edit", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        startEditingExpense(position);
                    } else {
                        deleteExpense(position);
                    }
                })
                .show();
    }

    private void startEditingExpense(int position) {
        editingPosition = position;
        String item = expensesList.get(position);

        // Assuming the format is "description: RM amount (timestamp)"
        String description = item.substring(0, item.indexOf(":"));

        // Extracting the amount by splitting at "RM" and getting the part after it
        String amountPart = item.substring(item.indexOf("RM") + 2);
        String amount = amountPart.split(" ")[0]; // This extracts the first part (amount) before any space

        // Set the extracted values to the EditText fields
        expenseDescriptionEditText.setText(description);
        expenseAmountEditText.setText(amount);
        addExpenseButton.setText("Update Expense");
    }

    private void showAddBudgetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Monthly Budget");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String budgetText = input.getText().toString();
            if (TextUtils.isEmpty(budgetText)) {
                showToast("Enter a valid amount");
                return;
            }

            try {
                double budget = Double.parseDouble(budgetText);
                saveBudget(budget);
            } catch (NumberFormatException e) {
                showToast("Enter a valid amount");
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveBudget(double budget) {
        Map<String, Object> budgetData = new HashMap<>();
        budgetData.put("budget", budget);
        budgetData.put("timestamp", Timestamp.now()); // Save the current timestamp

        db.collection("users").document(userId).collection("budget")
                .whereGreaterThanOrEqualTo("timestamp", getStartOfMonthTimestamp())
                .whereLessThanOrEqualTo("timestamp", getEndOfMonthTimestamp())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        document.getReference().delete(); // Delete old budget documents for the month
                    }

                    // Save the new budget
                    db.collection("users").document(userId).collection("budget")
                            .add(budgetData)
                            .addOnSuccessListener(aVoid -> {
                                budgetTextView.setText("Monthly Budget: RM " + String.format("%.2f", budget));
                                showToast("Budget saved");

                                // Optionally reload the budget to confirm
                                loadBudget();
                            })
                            .addOnFailureListener(e -> showToast("Failed to save budget"));
                })
                .addOnFailureListener(e -> showToast("Failed to check existing budget documents"));
    }

    private Timestamp getStartOfMonthTimestamp() {
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        int currentMonth = calendar.get(Calendar.MONTH);

        calendar.set(currentYear, currentMonth, 1, 0, 0, 0);
        return new Timestamp(calendar.getTime());
    }

    private Timestamp getEndOfMonthTimestamp() {
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        int currentMonth = calendar.get(Calendar.MONTH);

        calendar.set(currentYear, currentMonth, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59);
        return new Timestamp(calendar.getTime());
    }

    private boolean isValidDouble(String amountText) {
        try {
            Double.parseDouble(amountText);
            return true; // If successful, return true
        } catch (NumberFormatException e) {
            return false; // If there's an error, return false
        }
    }

    private void showToast(String message) {
        Toast.makeText(ExpenseActivity.this, message, Toast.LENGTH_SHORT).show();
    }
}
