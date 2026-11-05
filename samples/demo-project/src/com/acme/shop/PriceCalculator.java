package com.acme.shop;

/**
 * Sample code for demonstrating the AI Code Review Agent.
 *
 * This class is intentionally small and mostly correct, so that the test-generation feature
 * has a clean target with clear boundary conditions to write tests against.
 */
public class PriceCalculator {

    private static final double TAX_RATE = 0.13;
    private static final int BULK_THRESHOLD = 10;
    private static final double BULK_DISCOUNT = 0.10;

    /**
     * Calculates the price of a line item, applying a bulk discount and then tax.
     *
     * @param unitPrice price of one unit; must not be negative
     * @param quantity  number of units; must be at least one
     * @return the total including tax, rounded to the nearest cent
     * @throws IllegalArgumentException when the inputs are out of range
     */
    public double lineTotal(double unitPrice, int quantity) {
        if (unitPrice < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        double subtotal = unitPrice * quantity;
        if (quantity >= BULK_THRESHOLD) {
            subtotal = subtotal * (1 - BULK_DISCOUNT);
        }
        double withTax = subtotal * (1 + TAX_RATE);
        return Math.round(withTax * 100.0) / 100.0;
    }

    /**
     * Applies a percentage discount code.
     *
     * @param total   the amount before the discount
     * @param percent discount percentage between 0 and 100
     * @return the discounted amount, never below zero
     */
    public double applyDiscount(double total, int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be between 0 and 100");
        }
        double discounted = total - (total * percent / 100.0);
        return Math.max(0.0, Math.round(discounted * 100.0) / 100.0);
    }

    /**
     * Works out the shipping cost.
     *
     * @param weightKilograms parcel weight
     * @param express         whether express delivery was chosen
     * @return the shipping cost in dollars
     */
    public double shippingCost(double weightKilograms, boolean express) {
        if (weightKilograms <= 0) {
            return 0.0;
        }
        double base = weightKilograms <= 1.0 ? 4.99 : 4.99 + (weightKilograms - 1.0) * 2.50;
        return express ? Math.round((base + 14.99) * 100.0) / 100.0
                       : Math.round(base * 100.0) / 100.0;
    }
}
