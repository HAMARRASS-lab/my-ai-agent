package com.hamza.foodordringsystem.myaiagent.reports;

import com.hamza.foodordringsystem.myaiagent.auth.AuthController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final SavedReportRepository reports;

    public ReportController(SavedReportRepository reports) {
        this.reports = reports;
    }

    public record ReportDetail(Long id, String company, Instant createdAt, String markdown) {
        static ReportDetail of(SavedReport report) {
            return new ReportDetail(report.getId(), report.getCompany(), report.getCreatedAt(), report.getMarkdown());
        }
    }

    @GetMapping
    public List<ReportSummary> list(@SessionAttribute(AuthController.SESSION_USER_ID) Long userId) {
        return reports.findTop50ByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @GetMapping("/{id}")
    public ReportDetail get(@PathVariable Long id, @SessionAttribute(AuthController.SESSION_USER_ID) Long userId) {
        return ReportDetail.of(find(id, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @SessionAttribute(AuthController.SESSION_USER_ID) Long userId) {
        reports.delete(find(id, userId));
        return ResponseEntity.noContent().build();
    }

    private SavedReport find(Long id, Long userId) {
        return reports.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found."));
    }
}
