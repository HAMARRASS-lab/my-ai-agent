package com.hamza.foodordringsystem.myaiagent.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {

    List<ReportSummary> findTop50ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    // Always scoped by user so nobody can open or delete another user's report by guessing an id.
    Optional<SavedReport> findByIdAndUserId(Long id, Long userId);
}
