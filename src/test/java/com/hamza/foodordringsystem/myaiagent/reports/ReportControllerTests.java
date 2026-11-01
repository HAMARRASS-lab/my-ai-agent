package com.hamza.foodordringsystem.myaiagent.reports;

import com.hamza.foodordringsystem.myaiagent.auth.AuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.ai.openai.api-key=test-key", "spring.datasource.url=jdbc:h2:mem:reports-test"})
@AutoConfigureMockMvc
class ReportControllerTests {

    private static final Long ME = 1L;
    private static final Long SOMEONE_ELSE = 2L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SavedReportRepository reports;

    @BeforeEach
    void clearReports() {
        reports.deleteAll();
    }

    @Test
    void listReturnsOnlyOwnReportsNewestFirstWithoutMarkdown() throws Exception {
        reports.save(new SavedReport(ME, "Renault", "# Renault"));
        reports.save(new SavedReport(SOMEONE_ELSE, "Apple", "# Apple"));
        reports.save(new SavedReport(ME, "Toyota", "# Toyota"));

        mockMvc.perform(get("/api/reports").sessionAttr(AuthController.SESSION_USER_ID, ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].company").value("Toyota"))
                .andExpect(jsonPath("$[1].company").value("Renault"))
                .andExpect(jsonPath("$[0].markdown").doesNotExist());
    }

    @Test
    void getReturnsFullReport() throws Exception {
        SavedReport saved = reports.save(new SavedReport(ME, "Renault", "# Renault report"));

        mockMvc.perform(get("/api/reports/" + saved.getId()).sessionAttr(AuthController.SESSION_USER_ID, ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("Renault"))
                .andExpect(jsonPath("$.markdown").value("# Renault report"));
    }

    @Test
    void otherUsersReportIsNotFound() throws Exception {
        SavedReport theirs = reports.save(new SavedReport(SOMEONE_ELSE, "Apple", "# Apple"));

        mockMvc.perform(get("/api/reports/" + theirs.getId()).sessionAttr(AuthController.SESSION_USER_ID, ME))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/reports/" + theirs.getId()).sessionAttr(AuthController.SESSION_USER_ID, ME))
                .andExpect(status().isNotFound());

        assertThat(reports.existsById(theirs.getId())).isTrue();
    }

    @Test
    void deleteRemovesOwnReport() throws Exception {
        SavedReport saved = reports.save(new SavedReport(ME, "Renault", "# Renault"));

        mockMvc.perform(delete("/api/reports/" + saved.getId()).sessionAttr(AuthController.SESSION_USER_ID, ME))
                .andExpect(status().isNoContent());

        assertThat(reports.existsById(saved.getId())).isFalse();
    }

    @Test
    void reportsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/reports/1")).andExpect(status().isUnauthorized());
    }
}
