package com.multiagent.repository;

import com.multiagent.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ProductEntity}.
 * All CRUD + paging/sorting methods are provided by {@link JpaRepository}.
 */
@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Integer> {

    /**
     * Case-insensitive keyword search across both name and category columns.
     * Equivalent to: WHERE LOWER(name) LIKE %keyword% OR LOWER(category) LIKE %keyword%
     */
    List<ProductEntity> findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(
            String name, String category);

    /**
     * Products at or below the given stock threshold, sorted by quantity ascending.
     */
    List<ProductEntity> findByQuantityLessThanEqualOrderByQuantityAsc(int threshold);

    /**
     * Sum of price × quantity across all products.
     * Returns 0.0 when the table is empty.
     */
    @Query("SELECT COALESCE(SUM(p.price * p.quantity), 0.0) FROM ProductEntity p")
    Double calculateTotalInventoryValue();
}
