package assignment.a202sg1tester;

public class Expense {
    private String description;
    private double amount;
    private String month; // Added month property

    public Expense(String description, double amount) {} // Required for Firestore

    public Expense(String description, double amount, String month) {
        this.description = description;
        this.amount = amount;
        this.month = month; // Initialize the month property
    }

    public String getDescription() {
        return description;
    }

    public double getAmount() {
        return amount;
    }

    public String getMonth() {
        return month; // Getter for the month property
    }
}
