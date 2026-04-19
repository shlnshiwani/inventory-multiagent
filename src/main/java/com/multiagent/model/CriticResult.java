package com.multiagent.model;

/**
 * Immutable result from the Critic Agent's evaluation of a specialist agent's output.
 *
 * @param score    quality score 1–10 (threshold for passing: {@value com.multiagent.agents.CriticAgent#PASS_THRESHOLD})
 * @param feedback one-sentence explanation of the score
 */
public record CriticResult(int score, String feedback) {

    /** Returns {@code true} when this result would allow the workflow to proceed. */
    public boolean passes() {
        return score >= com.multiagent.agents.CriticAgent.PASS_THRESHOLD;
    }
}
