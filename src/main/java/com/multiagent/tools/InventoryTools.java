package com.multiagent.tools;

import com.multiagent.db.InventoryRepository;
import com.multiagent.model.Product;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool-1  addProduct          — inserts a new product into H2
 * Tool-2  searchProducts      — keyword search across name / category
 * Tool-3  getLowStockProducts — finds products at or below a stock threshold
 */
@Component
public class InventoryTools {

    private static final Logger log = LoggerFactory.getLogger(InventoryTools.class);

    private final InventoryRepository repo;

    public InventoryTools(InventoryRepository repo) {
        this.repo = repo;
    }

    // ── Tool 1 ───────────────────────────────────────────────────────────────

    @Tool("Add a new product to the inventory. Returns the created product with its ID.")
    public String addProduct(
            @P("Product name")                        String name,
            @P("Product category (e.g. Electronics)")  String category,
            @P("Unit price in USD")                    double price,
            @P("Initial stock quantity")               int quantity) {
        log.info("[Tool-1] addProduct name='{}' category='{}' price={} qty={}",
                 name, category, price, quantity);
        Product p = repo.addProduct(name, category, price, quantity);
        return "Product added: " + p;
    }

    // ── Tool 2 ───────────────────────────────────────────────────────────────

    @Tool("Search the inventory for products matching a keyword in name or category.")
    public String searchProducts(@P("Search keyword (partial match)") String keyword) {
        log.info("[Tool-2] searchProducts keyword='{}'", keyword);
        List<Product> results = repo.searchProducts(keyword);
        if (results.isEmpty()) return "No products found for keyword: '" + keyword + "'";
        return "Found %d product(s):\n%s".formatted(results.size(),
                results.stream().map(Product::toString).collect(Collectors.joining("\n")));
    }

    // ── Tool 3 ───────────────────────────────────────────────────────────────

    @Tool("Get all products whose stock quantity is at or below the given threshold.")
    public String getLowStockProducts(@P("Stock quantity threshold (inclusive)") int threshold) {
        log.info("[Tool-3] getLowStockProducts threshold={}", threshold);
        List<Product> low = repo.getLowStockProducts(threshold);
        if (low.isEmpty()) return "No products at or below threshold of " + threshold;
        return "Low-stock products (qty ≤ %d):\n%s".formatted(threshold,
                low.stream()
                   .map(p -> "  - %s  qty=%d".formatted(p.name(), p.quantity()))
                   .collect(Collectors.joining("\n")));
    }
}
