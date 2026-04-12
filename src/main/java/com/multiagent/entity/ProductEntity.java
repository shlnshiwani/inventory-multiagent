package com.multiagent.entity;

import jakarta.persistence.*;

/**
 * JPA entity mapped to the {@code products} table.
 * Hibernate generates the DDL from this class (ddl-auto=create-drop).
 */
@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private Integer quantity;

    /** Required by JPA spec. */
    protected ProductEntity() {}

    public ProductEntity(String name, String category, Double price, Integer quantity) {
        this.name     = name;
        this.category = category;
        this.price    = price;
        this.quantity = quantity;
    }

    public Integer getId()       { return id; }
    public String  getName()     { return name; }
    public String  getCategory() { return category; }
    public Double  getPrice()    { return price; }
    public Integer getQuantity() { return quantity; }

    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    @Override
    public String toString() {
        return "Product{id=%d, name='%s', category='%s', price=$%.2f, qty=%d}"
                .formatted(id, name, category, price, quantity);
    }
}
