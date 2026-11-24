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

// Saved reports of the logged-in user. Login is enforced by LoginRequiredInterceptor;
// reports of other users answer 404 so their ids can't be probed.
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final AnalysisReportRepository reports;

    public ReportController(AnalysisReportRepository reports) {
        this.reports = reports;
    }

    public record Summary(Long id, String company, Instant createdAt, long durationMs) {
        static Summary of(AnalysisReport report) {
            return new Summary(report.getId(), report.getCompany(), report.getCreatedAt(), report.getDurationMs());
        }
    }

    public record Detail(Long id, String company, Instant createdAt, long durationMs, String content) {
        static Detail of(AnalysisReport report) {
            return new Detail(report.getId(), report.getCompany(), report.getCreatedAt(), report.getDurationMs(), report.getContent());
        }
    }

    @GetMapping
    public List<Summary> list(@SessionAttribute(AuthController.SESSION_USER_ID) Long userId) {
        return reports.findTop20ByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(Summary::of).toList();
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable Long id, @SessionAttribute(AuthController.SESSION_USER_ID) Long userId) {
        return Detail.of(owned(id, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @SessionAttribute(AuthController.SESSION_USER_ID) Long userId) {
        reports.delete(owned(id, userId));
        return ResponseEntity.noContent().build();
    }

    private AnalysisReport owned(Long id, Long userId) {
        return reports.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found."));
    }
}
