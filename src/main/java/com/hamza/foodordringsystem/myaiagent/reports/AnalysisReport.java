package com.hamza.foodordringsystem.myaiagent.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

// A financial analysis generated for a user, kept so it can be reopened without calling the model again.
@Entity
@Table(name = "analysis_report", indexes = @Index(name = "idx_report_user", columnList = "userId, createdAt"))
public class AnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String company;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private Instant createdAt;

    protected AnalysisReport() {
    }

    public AnalysisReport(Long userId, String company, String content, long durationMs) {
        this.userId = userId;
        this.company = company;
        this.content = content;
        this.durationMs = durationMs;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCompany() {
        return company;
    }

    public String getContent() {
        return content;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
