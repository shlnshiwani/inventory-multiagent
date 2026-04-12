package com.multiagent.tools;

import com.multiagent.db.InventoryRepository;
import com.multiagent.model.Report;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool-6  saveReport  — persists a generated report to H2
 * Tool-7  listReports — retrieves all saved report titles
 */
@Component
public class ReportTools {

    private static final Logger log = LoggerFactory.getLogger(ReportTools.class);

    private final InventoryRepository repo;

    public ReportTools(InventoryRepository repo) {
        this.repo = repo;
    }

    // ── Tool 6 ───────────────────────────────────────────────────────────────

    @Tool("Save an analysis report to the database. Returns the saved report ID.")
    public String saveReport(
            @P("Report title")                                              String title,
            @P("Full report content (executive summary, findings, recommendations)") String content) {
        log.info("[Tool-6] saveReport title='{}'", title);
        Report report = repo.saveReport(title, content);
        return "Report saved: id=%d title='%s' at %s"
                .formatted(report.id(), report.title(), report.createdAt());
    }

    // ── Tool 7 ───────────────────────────────────────────────────────────────

    @Tool("List all reports previously saved to the database.")
    public String listReports() {
        log.info("[Tool-7] listReports");
        List<Report> reports = repo.getAllReports();
        if (reports.isEmpty()) return "No reports have been saved yet.";
        return "Saved reports (%d):\n%s".formatted(reports.size(),
                reports.stream()
                       .map(r -> "  [%d] '%s' (created: %s)".formatted(
                               r.id(), r.title(), r.createdAt()))
                       .collect(Collectors.joining("\n")));
    }
}
