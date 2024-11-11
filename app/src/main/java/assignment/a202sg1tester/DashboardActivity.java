package assignment.a202sg1tester;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;

public class DashboardActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "BudgetAlertChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

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
    private int fetchCompleteCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dashboard_page);

        initializeViews();
        initializeFirebase();
        initializeActivityResultLaunchers();
        setOnClickListeners();
        fetchMonthlyData(); // Fetch data for the current month
        checkNotificationPermission(); // Check and request notification permission
        createNotificationChannel();
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
        userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (userId == null) {
            Log.e("DashboardActivity", "User not logged in");
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Budget Alert";
            String description = "Channel for budget exceed notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void initializeActivityResultLaunchers() {
        addExpenseLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> updatedExpensesList = result.getData().getStringArrayListExtra(Constants.EXTRA_UPDATED_EXPENSES);
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
                        ArrayList<String> updatedIncomeList = result.getData().getStringArrayListExtra(Constants.EXTRA_UPDATED_INCOMES);
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
        fetchCompleteCount = 0; // Reset fetch completion counter
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1); // Start of the month
        Timestamp startOfMonth = new Timestamp(calendar.getTime());

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)); // End of the month
        Timestamp endOfMonth = new Timestamp(calendar.getTime());

        fetchIncomeForMonth(startOfMonth, endOfMonth);
        fetchExpensesForMonth(startOfMonth, endOfMonth);
        fetchBudgetForMonth(startOfMonth, endOfMonth);
    }

    private void onFetchComplete() {
        fetchCompleteCount++;
        if (fetchCompleteCount == 3) { // Ensure all three fetches have completed
            calculateAndDisplayTotals(); // Display totals after all data has been fetched
            checkBudgetExceed(); // Check budget only after totals are calculated
        }
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
                            double amount = document.getDouble("amount") != null ? document.getDouble("amount") : 0;
                            totalIncome += amount;
                            incomeList.add(source + ": RM" + amount);
                        }
                    }
                    onFetchComplete(); // Indicate this fetch has completed
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
                            double amount = document.getDouble("amount") != null ? document.getDouble("amount") : 0;
                            totalExpenses += amount;
                            expensesList.add(category + ": RM" + amount);
                        }
                    }
                    onFetchComplete(); // Indicate this fetch has completed
                });
    }

    private void fetchBudgetForMonth(Timestamp startOfMonth, Timestamp endOfMonth) {
        CollectionReference budgetRef = db.collection("users").document(userId).collection("budget");
        budgetRef.whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .whereLessThanOrEqualTo("timestamp", endOfMonth)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        monthlyBudget = document.getDouble("budget") != null ? document.getDouble("budget") : 0;
                        budgetTextView.setText(String.format("Monthly Budget: RM%.2f", monthlyBudget));
                    } else {
                        budgetTextView.setText("Monthly Budget: RM0.00");
                        monthlyBudget = 0.0;
                    }
                    onFetchComplete(); // Indicate this fetch has completed
                })
                .addOnFailureListener(e -> {
                    Log.e("BudgetFetch", "Failed to fetch budget", e);
                    budgetTextView.setText("Monthly Budget: RM0.00");
                    monthlyBudget = 0.0;
                    showToast("Failed to load budget");
                    onFetchComplete(); // Ensure fetch count increments even on failure
                });
    }

    private void calculateAndDisplayTotals() {
        double availableBalance = monthlyBudget - totalExpenses;
        totalIncomeTextView.setText(String.format("Total Income: RM%.2f", totalIncome));
        totalExpensesTextView.setText(String.format("Total Expenses: RM%.2f", totalExpenses));
        availableBalanceTextView.setText(String.format("RM%.2f", availableBalance));
    }

    private void checkBudgetExceed() {
        if (totalExpenses > monthlyBudget) {
            sendBudgetExceedNotification();
        }
    }

    private void sendBudgetExceedNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Notification", "Notification permission not granted.");
            return;
        }

        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Budget Alert")
                .setContentText("Your expenses have exceeded your budget!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            Log.e("NotificationError", "Error sending budget exceed notification", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchMonthlyData();
    }

    private void showToast(String message) {
        Toast.makeText(DashboardActivity.this, message, Toast.LENGTH_SHORT).show();
    }
}
