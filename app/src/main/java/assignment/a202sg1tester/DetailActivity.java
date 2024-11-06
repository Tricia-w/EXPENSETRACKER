package assignment.a202sg1tester;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {

    private Spinner monthSpinner;
    private ListView detailListView;
    private TextView totalIncomeTextView, totalExpensesTextView;
    private ArrayList<Income> incomes = new ArrayList<>();
    private ArrayList<Expense> expenses = new ArrayList<>();
    private Button backButton;

    private FirebaseFirestore db;
    private String userId;
    private SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detail_page);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        monthSpinner = findViewById(R.id.month_spinner);
        detailListView = findViewById(R.id.detail_list_view);
        totalIncomeTextView = findViewById(R.id.total_income_text_view);
        totalExpensesTextView = findViewById(R.id.total_expenses_text_view);
        backButton = findViewById(R.id.back_button);

        setupMonthSpinner();
        fetchDataFromFirebase();

        backButton.setOnClickListener(v -> finish());
    }

    private void setupMonthSpinner() {
        String[] months = {"Month", "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, months);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        monthSpinner.setAdapter(adapter);

        monthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) { // Ignore the "Month" selection
                    String selectedMonth = months[position];
                    filterDataByMonth(selectedMonth);
                } else {
                    totalIncomeTextView.setText("Total Income: RM0.00");
                    totalExpensesTextView.setText("Total Expenses: RM0.00");
                    detailListView.setAdapter(null); // Clear the list
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    private void fetchDataFromFirebase() {
        fetchIncomeData();
        fetchExpenseData();
    }

    private void fetchIncomeData() {
        CollectionReference incomeRef = db.collection("users").document(userId).collection("income");
        incomeRef.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    incomes.clear();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String source = document.getString("source");
                        double amount = document.getDouble("amount");
                        Timestamp timestamp = document.getTimestamp("timestamp");

                        if (source != null && timestamp != null) {
                            incomes.add(new Income(source, amount, getMonthFromTimestamp(timestamp)));
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load income data", Toast.LENGTH_SHORT).show());
    }

    private void fetchExpenseData() {
        CollectionReference expenseRef = db.collection("users").document(userId).collection("expenses");
        expenseRef.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    expenses.clear();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String description = document.getString("description");
                        double amount = document.getDouble("amount");
                        Timestamp timestamp = document.getTimestamp("timestamp");

                        if (description != null && timestamp != null) {
                            expenses.add(new Expense(description, amount, getMonthFromTimestamp(timestamp)));
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load expense data", Toast.LENGTH_SHORT).show());
    }

    private String getMonthFromTimestamp(Timestamp timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(timestamp.toDate());
        return monthFormat.format(calendar.getTime());
    }

    private void filterDataByMonth(String month) {
        double totalIncome = 0.0;
        double totalExpenses = 0.0;
        ArrayList<HashMap<String, String>> itemList = new ArrayList<>();

        for (Income income : incomes) {
            if (income.getMonth().equals(month)) {
                totalIncome += income.getAmount();
                HashMap<String, String> map = new HashMap<>();
                map.put("type", "Income: " + income.getSource());
                map.put("amount", "RM" + String.format("%.2f", income.getAmount()));
                itemList.add(map);
            }
        }

        for (Expense expense : expenses) {
            if (expense.getMonth().equals(month)) {
                totalExpenses += Math.abs(expense.getAmount());
                HashMap<String, String> map = new HashMap<>();
                map.put("type", "Expense: " + expense.getDescription());
                map.put("amount", "-RM" + String.format("%.2f", Math.abs(expense.getAmount())));
                itemList.add(map);
            }
        }

        totalIncomeTextView.setText("Total Income: RM" + String.format("%.2f", totalIncome));
        totalExpensesTextView.setText("Total Expenses: RM" + String.format("%.2f", totalExpenses));

        SimpleAdapter adapter = new SimpleAdapter(
                this,
                itemList,
                android.R.layout.simple_list_item_2,
                new String[]{"type", "amount"},
                new int[]{android.R.id.text1, android.R.id.text2}
        );

        detailListView.setAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
