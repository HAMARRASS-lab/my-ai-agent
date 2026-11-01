package com.hamza.foodordringsystem.myaiagent.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// A generated dossier, kept so the user can reopen it later without another AI call.
@Entity
@Table(name = "saved_report")
public class SavedReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String company;

    @Column(nullable = false, length = 50_000)
    private String markdown;

    @Column(nullable = false)
    private Instant createdAt;

    protected SavedReport() {
    }

    public SavedReport(Long userId, String company, String markdown) {
        this.userId = userId;
        this.company = company;
        this.markdown = markdown;
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

    public String getMarkdown() {
        return markdown;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
