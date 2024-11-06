package assignment.a202sg1tester;

import java.io.Serializable;

public class Income implements Serializable {  // Implement Serializable
    private String source;
    private double amount;
    private String month; // Add this field

    public Income(String description, double amount) {} // Required for Firestore

    public Income(String source, double amount, String month) {
        this.source = source;
        this.amount = amount;
        this.month = month; // Initialize month
    }

    public String getSource() {
        return source;
    }

    public double getAmount() {
        return amount;
    }

    public String getMonth() {
        return month; // Add this getter
    }
}
