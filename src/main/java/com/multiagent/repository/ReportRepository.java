package com.multiagent.repository;

import com.multiagent.entity.ReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ReportEntity}.
 */
@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, Integer> {

    /** All reports ordered by creation time (most recent last). */
    List<ReportEntity> findAllByOrderByCreatedAtAsc();
}
