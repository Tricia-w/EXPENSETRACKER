package assignment.a202sg1tester;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private TextView totalIncomeTextView, totalExpensesTextView, availableBalanceTextView, budgetTextView;
    private ArrayList<String> expensesList = new ArrayList<>();
    private ArrayList<String> incomeList = new ArrayList<>();
    private double totalIncome = 0.0;
    private double totalExpenses = 0.0;
    private double monthlyBudget = 0.0;

    private ActivityResultLauncher<Intent> addExpenseLauncher;
    private ActivityResultLauncher<Intent> addIncomeLauncher;

    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dashboard_page);

        initializeViews();
        initializeFirebase();
        initializeActivityResultLaunchers();
        setOnClickListeners();
        fetchMonthlyData();  // Fetch data for the current month
    }

    private void initializeViews() {
        totalIncomeTextView = findViewById(R.id.total_income_text_view);
        totalExpensesTextView = findViewById(R.id.total_expenses_text_view);
        availableBalanceTextView = findViewById(R.id.balanceAmount);
        budgetTextView = findViewById(R.id.monthly_budget_text_view);
    }

    private void initializeFirebase() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        userId = auth.getCurrentUser().getUid();
    }

    private void initializeActivityResultLaunchers() {
        addExpenseLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> updatedExpensesList = result.getData()
                                .getStringArrayListExtra(Constants.EXTRA_UPDATED_EXPENSES);
                        if (updatedExpensesList != null) {
                            expensesList = updatedExpensesList;
                            fetchMonthlyData(); // Refresh all data after returning from ExpenseActivity
                        }
                    }
                }
        );

        addIncomeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> updatedIncomeList = result.getData()
                                .getStringArrayListExtra(Constants.EXTRA_UPDATED_INCOMES);
                        if (updatedIncomeList != null) {
                            incomeList = updatedIncomeList;
                            fetchMonthlyData(); // Refresh all data after returning from IncomeActivity
                        }
                    }
                }
        );
    }

    private void setOnClickListeners() {
        findViewById(R.id.view_details_button).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, DetailActivity.class);
            intent.putExtra("incomes", incomeList);
            intent.putExtra("expenses", expensesList);
            startActivity(intent);
        });

        findViewById(R.id.add_expense_button).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ExpenseActivity.class);
            intent.putStringArrayListExtra(Constants.EXTRA_UPDATED_EXPENSES, expensesList);
            addExpenseLauncher.launch(intent);
        });

        findViewById(R.id.add_income_button).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, IncomeActivity.class);
            intent.putStringArrayListExtra(Constants.EXTRA_UPDATED_INCOMES, incomeList);
            addIncomeLauncher.launch(intent);
        });

        findViewById(R.id.settings_button).setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, SettingActivity.class);
            startActivity(intent);
        });
    }

    private void fetchMonthlyData() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1); // Start of the month
        Timestamp startOfMonth = new Timestamp(calendar.getTime());

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)); // End of the month
        Timestamp endOfMonth = new Timestamp(calendar.getTime());

        fetchIncomeForMonth(startOfMonth, endOfMonth);
        fetchExpensesForMonth(startOfMonth, endOfMonth);
        fetchBudgetForMonth(startOfMonth, endOfMonth);
    }

    private void fetchIncomeForMonth(Timestamp startOfMonth, Timestamp endOfMonth) {
        CollectionReference incomeRef = db.collection("users").document(userId).collection("income");
        incomeRef.whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .whereLessThanOrEqualTo("timestamp", endOfMonth)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        totalIncome = 0.0;
                        incomeList.clear();
                        for (DocumentSnapshot document : task.getResult()) {
                            String source = document.getString("source");
                            double amount = document.getDouble("amount");
                            totalIncome += amount;
                            incomeList.add(source + ": RM" + amount);
                        }
                        calculateAndDisplayTotals();
                    }
                });
    }

    private void fetchExpensesForMonth(Timestamp startOfMonth, Timestamp endOfMonth) {
        CollectionReference expensesRef = db.collection("users").document(userId).collection("expenses");
        expensesRef.whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .whereLessThanOrEqualTo("timestamp", endOfMonth)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        totalExpenses = 0.0;
                        expensesList.clear();
                        for (DocumentSnapshot document : task.getResult()) {
                            String category = document.getString("category");
                            double amount = document.getDouble("amount");
                            totalExpenses += amount;
                            expensesList.add(category + ": RM" + amount);
                        }
                        calculateAndDisplayTotals();
                    }
                });
    }

    private void fetchBudgetForMonth(Timestamp startOfMonth, Timestamp endOfMonth) {
        CollectionReference budgetRef = db.collection("users").document(userId).collection("budget");
        budgetRef.whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .whereLessThanOrEqualTo("timestamp", endOfMonth)
                .limit(1) // Assuming only one budget document per month
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        monthlyBudget = document.getDouble("budget");
                        budgetTextView.setText(String.format("Monthly Budget: RM%.2f", monthlyBudget));
                        Log.d("BudgetFetch", "Budget found: " + monthlyBudget);
                    } else {
                        budgetTextView.setText("Monthly Budget: RM0.00");
                        monthlyBudget = 0.0;
                        Log.d("BudgetFetch", "No budget document found.");
                    }
                    calculateAndDisplayTotals();
                })
                .addOnFailureListener(e -> {
                    Log.e("BudgetFetch", "Failed to fetch budget", e);
                    budgetTextView.setText("Monthly Budget: RM0.00");
                    showToast("Failed to load budget");
                });
    }

    private void calculateAndDisplayTotals() {
        double availableBalance = monthlyBudget - totalExpenses; // Calculate available balance as budget - expenses

        totalIncomeTextView.setText(String.format("Total Income: RM%.2f", totalIncome));
        totalExpensesTextView.setText(String.format("Total Expenses: RM%.2f", totalExpenses));
        availableBalanceTextView.setText(String.format("RM%.2f", availableBalance));
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchMonthlyData(); // Re-fetch data when returning to the dashboard
    }

    private void showToast(String message) {
        Toast.makeText(DashboardActivity.this, message, Toast.LENGTH_SHORT).show();
    }
}
