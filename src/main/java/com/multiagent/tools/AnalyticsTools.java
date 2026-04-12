package com.multiagent.tools;

import com.multiagent.db.InventoryRepository;
import com.multiagent.model.Product;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool-4  calculateInventoryValue — total monetary value (price × quantity)
 * Tool-5  getCategoryBreakdown    — per-category aggregation summary
 */
@Component
public class AnalyticsTools {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsTools.class);

    private final InventoryRepository repo;

    public AnalyticsTools(InventoryRepository repo) {
        this.repo = repo;
    }

    // ── Tool 4 ───────────────────────────────────────────────────────────────

    @Tool("Calculate the total monetary value of all inventory (sum of price × quantity).")
    public String calculateInventoryValue() {
        log.info("[Tool-4] calculateInventoryValue");
        double total = repo.calculateInventoryValue();
        return "Total inventory value: $%.2f".formatted(total);
    }

    // ── Tool 5 ───────────────────────────────────────────────────────────────

    @Tool("Get an aggregated breakdown of inventory by category: "
        + "item count, total quantity, and total value per category.")
    public String getCategoryBreakdown() {
        log.info("[Tool-5] getCategoryBreakdown");
        List<Product> all = repo.getAllProducts();
        if (all.isEmpty()) return "Inventory is empty.";

        Map<String, List<Product>> byCategory =
                all.stream().collect(Collectors.groupingBy(Product::category));

        StringBuilder sb = new StringBuilder("Category breakdown:\n");
        byCategory.forEach((cat, products) -> {
            int    totalQty = products.stream().mapToInt(Product::quantity).sum();
            double totalVal = products.stream()
                                      .mapToDouble(p -> p.price() * p.quantity())
                                      .sum();
            sb.append("  [%s] items=%d  total-qty=%d  value=$%.2f\n"
                      .formatted(cat, products.size(), totalQty, totalVal));
        });
        return sb.toString().trim();
    }
}
