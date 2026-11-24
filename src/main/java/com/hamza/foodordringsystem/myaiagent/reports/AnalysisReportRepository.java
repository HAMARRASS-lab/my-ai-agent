package com.hamza.foodordringsystem.myaiagent.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    List<AnalysisReport> findTop20ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<AnalysisReport> findByIdAndUserId(Long id, Long userId);
}
