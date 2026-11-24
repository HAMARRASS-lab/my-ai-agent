package com.hamza.foodordringsystem.myaiagent.reports;

import com.hamza.foodordringsystem.myaiagent.MyAiAgentController;
import com.hamza.foodordringsystem.myaiagent.auth.AuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.ai.openai.api-key=test-key", "spring.datasource.url=jdbc:h2:mem:reports-test"})
@AutoConfigureMockMvc
class ReportControllerTests {

    private static final long ALICE = 1L;
    private static final long BOB = 2L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AnalysisReportRepository reports;

    @MockitoBean
    ChatModel chatModel;

    @BeforeEach
    void clearReports() {
        reports.deleteAll();
    }

    @Test
    void analysisIsSavedForTheUserAndCanBeReopened() throws Exception {
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("# Renault report")))));

        String id = mockMvc.perform(get("/financialAnalysis").param("company", "  Renault ")
                        .sessionAttr(AuthController.SESSION_USER_ID, ALICE))
                .andExpect(status().isOk())
                .andExpect(header().exists(MyAiAgentController.REPORT_ID_HEADER))
                .andReturn().getResponse().getHeader(MyAiAgentController.REPORT_ID_HEADER);

        AnalysisReport saved = reports.findById(Long.valueOf(id)).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(ALICE);
        assertThat(saved.getCompany()).isEqualTo("Renault");

        mockMvc.perform(get("/api/reports").sessionAttr(AuthController.SESSION_USER_ID, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].company").value("Renault"))
                .andExpect(jsonPath("$[0].content").doesNotExist());

        mockMvc.perform(get("/api/reports/" + id).sessionAttr(AuthController.SESSION_USER_ID, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("# Renault report"));
    }

    @Test
    void listIsNewestFirstAndOnlyShowsOwnReports() throws Exception {
        reports.save(new AnalysisReport(ALICE, "Renault", "# R", 10));
        reports.save(new AnalysisReport(ALICE, "Apple", "# A", 10));
        reports.save(new AnalysisReport(BOB, "Toyota", "# T", 10));

        mockMvc.perform(get("/api/reports").sessionAttr(AuthController.SESSION_USER_ID, ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].company").value("Apple"))
                .andExpect(jsonPath("$[1].company").value("Renault"));
    }

    @Test
    void otherUsersReportsAreNotFound() throws Exception {
        Long bobsReport = reports.save(new AnalysisReport(BOB, "Toyota", "# T", 10)).getId();

        mockMvc.perform(get("/api/reports/" + bobsReport).sessionAttr(AuthController.SESSION_USER_ID, ALICE))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/reports/" + bobsReport).sessionAttr(AuthController.SESSION_USER_ID, ALICE))
                .andExpect(status().isNotFound());

        assertThat(reports.existsById(bobsReport)).isTrue();
    }

    @Test
    void deleteRemovesOwnReport() throws Exception {
        Long id = reports.save(new AnalysisReport(ALICE, "Renault", "# R", 10)).getId();

        mockMvc.perform(delete("/api/reports/" + id).sessionAttr(AuthController.SESSION_USER_ID, ALICE))
                .andExpect(status().isNoContent());

        assertThat(reports.existsById(id)).isFalse();
    }

    @Test
    void reportsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/reports/1")).andExpect(status().isUnauthorized());
    }
}
