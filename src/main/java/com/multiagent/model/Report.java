package com.multiagent.model;

import java.time.LocalDateTime;

/**
 * Immutable domain model for a saved report row.
 */
public record Report(int id, String title, String content, LocalDateTime createdAt) {

    @Override
    public String toString() {
        return "Report{id=%d, title='%s', createdAt=%s}".formatted(id, title, createdAt);
    }
}
