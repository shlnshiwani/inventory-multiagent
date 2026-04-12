package com.multiagent.model;

/**
 * Immutable domain model for a product row.
 */
public record Product(int id, String name, String category, double price, int quantity) {

    @Override
    public String toString() {
        return "Product{id=%d, name='%s', category='%s', price=$%.2f, qty=%d}"
                .formatted(id, name, category, price, quantity);
    }
}
