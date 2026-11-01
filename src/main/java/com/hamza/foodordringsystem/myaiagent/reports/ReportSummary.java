package com.hamza.foodordringsystem.myaiagent.reports;

import java.time.Instant;

// History list entry: everything except the markdown, so listing stays cheap.
public record ReportSummary(Long id, String company, Instant createdAt) {
}
