package com.multiagent.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity mapped to the {@code agent_executions} table.
 * One row per agent invocation — groups by {@code sessionId} for a full workflow run.
 */
@Entity
@Table(name = "agent_executions", indexes = {
    @Index(name = "idx_session", columnList = "session_id"),
    @Index(name = "idx_session_agent", columnList = "session_id, agent_name")
})
public class AgentExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "agent_name", nullable = false, length = 50)
    private String agentName;

    @Column(nullable = false)
    private Integer iteration;

    @Lob
    @Column(nullable = false)
    private String input;

    @Lob
    @Column(nullable = false)
    private String output;

    @CreationTimestamp
    @Column(name = "executed_at", updatable = false)
    private LocalDateTime executedAt;

    protected AgentExecutionEntity() {}

    public AgentExecutionEntity(String sessionId, String agentName,
                                int iteration, String input, String output) {
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.iteration = iteration;
        this.input     = input;
        this.output    = output;
    }

    public Integer       getId()        { return id; }
    public String        getSessionId() { return sessionId; }
    public String        getAgentName() { return agentName; }
    public Integer       getIteration() { return iteration; }
    public String        getInput()     { return input; }
    public String        getOutput()    { return output; }
    public LocalDateTime getExecutedAt(){ return executedAt; }
}
