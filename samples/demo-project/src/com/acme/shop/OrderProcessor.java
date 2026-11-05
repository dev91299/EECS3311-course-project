package com.acme.shop;

import java.util.ArrayList;
import java.util.List;

/**
 * Sample code for demonstrating the AI Code Review Agent.
 *
 * WARNING: this file contains deliberate defects, including inefficient algorithms and a
 * method that does far too much. It exists so the performance and maintainability review
 * strategies have something real to report.
 */
public class OrderProcessor {

    private List<Order> orders = new ArrayList<>();
    private List<Customer> customers = new ArrayList<>();

    /**
     * Deliberate defect: quadratic matching of orders against customers.
     */
    public List<String> buildOrderLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            for (int j = 0; j < customers.size(); j++) {
                if (orders.get(i).customerId == customers.get(j).id) {
                    // Deliberate defect: string concatenation inside a nested loop.
                    String label = "";
                    label += customers.get(j).name;
                    label += " - order #";
                    label += orders.get(i).id;
                    label += " - total ";
                    label += orders.get(i).total;
                    labels.add(label);
                }
            }
        }
        return labels;
    }

    /**
     * Deliberate defect: one method with at least four separate responsibilities -
     * validation, pricing, persistence and notification - and eight parameters.
     */
    public boolean process(String customerName, String email, String address, String postcode,
                           String itemCode, int quantity, double unitPrice, boolean express) {
        if (customerName == null || customerName.length() == 0) return false;
        if (email == null || !email.contains("@")) return false;
        if (quantity <= 0) return false;

        double total = unitPrice * quantity;
        if (quantity > 10) {
            total = total * 0.9;
        }
        if (express) {
            total = total + 14.99;
        }
        double tax = total * 0.13;
        total = total + tax;

        Order order = new Order();
        order.id = orders.size() + 1;
        order.total = total;
        order.customerId = findOrCreateCustomer(customerName, email);
        orders.add(order);

        System.out.println("Order placed for " + customerName + ", total " + total);
        System.out.println("Shipping to " + address + " " + postcode + " item " + itemCode);
        return true;
    }

    private int findOrCreateCustomer(String name, String email) {
        for (Customer customer : customers) {
            if (customer.name.equals(name)) {
                return customer.id;
            }
        }
        Customer created = new Customer();
        created.id = customers.size() + 1;
        created.name = name;
        created.email = email;
        customers.add(created);
        return created.id;
    }

    /** Deliberate defect: a public mutable field holding internal state. */
    public static class Order {
        public int id;
        public int customerId;
        public double total;
    }

    /** Deliberate defect: no encapsulation. */
    public static class Customer {
        public int id;
        public String name;
        public String email;
    }
}
