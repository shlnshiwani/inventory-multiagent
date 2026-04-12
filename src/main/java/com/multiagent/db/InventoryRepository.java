package com.multiagent.db;

import com.multiagent.entity.ProductEntity;
import com.multiagent.entity.ReportEntity;
import com.multiagent.model.Product;
import com.multiagent.model.Report;
import com.multiagent.repository.ProductRepository;
import com.multiagent.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Facade repository that translates between JPA entities and lightweight
 * domain records ({@link Product}, {@link Report}).
 *
 * Tools and services depend only on this class — the underlying Spring Data
 * JPA repositories ({@link ProductRepository}, {@link ReportRepository})
 * are hidden as implementation details.
 */
@Repository
public class InventoryRepository {

    private static final Logger log = LoggerFactory.getLogger(InventoryRepository.class);

    private final ProductRepository productRepo;
    private final ReportRepository  reportRepo;

    public InventoryRepository(ProductRepository productRepo,
                               ReportRepository  reportRepo) {
        this.productRepo = productRepo;
        this.reportRepo  = reportRepo;
    }

    // ── Products ──────────────────────────────────────────────────────────────

    public Product addProduct(String name, String category, double price, int quantity) {
        ProductEntity saved = productRepo.save(
                new ProductEntity(name, category, price, quantity));
        log.info("[Repo] addProduct id={} name='{}'", saved.getId(), saved.getName());
        return toRecord(saved);
    }

    public List<Product> searchProducts(String keyword) {
        return productRepo
                .findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(keyword, keyword)
                .stream()
                .map(this::toRecord)
                .toList();
    }

    public List<Product> getLowStockProducts(int threshold) {
        return productRepo
                .findByQuantityLessThanEqualOrderByQuantityAsc(threshold)
                .stream()
                .map(this::toRecord)
                .toList();
    }

    public double calculateInventoryValue() {
        Double val = productRepo.calculateTotalInventoryValue();
        return val != null ? val : 0.0;
    }

    public List<Product> getAllProducts() {
        return productRepo.findAll()
                .stream()
                .map(this::toRecord)
                .toList();
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    public Report saveReport(String title, String content) {
        ReportEntity saved = reportRepo.save(new ReportEntity(title, content));
        log.info("[Repo] saveReport id={} title='{}'", saved.getId(), saved.getTitle());
        return toRecord(saved);
    }

    public List<Report> getAllReports() {
        return reportRepo.findAllByOrderByCreatedAtAsc()
                .stream()
                .map(this::toRecord)
                .toList();
    }

    // ── Entity → Record mappers ───────────────────────────────────────────────

    private Product toRecord(ProductEntity e) {
        return new Product(e.getId(), e.getName(), e.getCategory(),
                           e.getPrice(), e.getQuantity());
    }

    private Report toRecord(ReportEntity e) {
        return new Report(e.getId(), e.getTitle(), e.getContent(), e.getCreatedAt());
    }
}
