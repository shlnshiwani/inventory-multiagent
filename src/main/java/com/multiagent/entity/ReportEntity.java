package com.multiagent.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity mapped to the {@code reports} table.
 */
@Entity
@Table(name = "reports")
public class ReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 300)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected ReportEntity() {}

    public ReportEntity(String title, String content) {
        this.title   = title;
        this.content = content;
    }

    public Integer       getId()        { return id; }
    public String        getTitle()     { return title; }
    public String        getContent()   { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "Report{id=%d, title='%s', createdAt=%s}".formatted(id, title, createdAt);
    }
}
