package com.hamza.foodordringsystem.myaiagent;

import com.hamza.foodordringsystem.myaiagent.auth.AuthController;
import com.hamza.foodordringsystem.myaiagent.reports.SavedReport;
import com.hamza.foodordringsystem.myaiagent.reports.SavedReportRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.ai.openai.api-key=test-key", "spring.datasource.url=jdbc:h2:mem:test"})
@AutoConfigureMockMvc
class MyAiAgentControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SavedReportRepository reports;

    @MockitoBean
    ChatModel chatModel;

    @Test
    void financialAnalysisSendsIdentityAndFinancialsToolsToModel() throws Exception {
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("# Renault report")))));

        mockMvc.perform(get("/financialAnalysis").param("company", "Renault").sessionAttr(AuthController.SESSION_USER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(content().string("# Renault report"));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(((ToolCallingChatOptions) prompt.getValue().getOptions()).getToolCallbacks())
                .extracting(tool -> tool.getToolDefinition().name())
                .containsExactly("countryIdentityInfo", "companyFinancials");
    }

    @Test
    void financialAnalysisSavesReportForUser() throws Exception {
        reports.deleteAll();
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("# Airbus report")))));

        String id = mockMvc.perform(get("/financialAnalysis").param("company", "  Airbus ").sessionAttr(AuthController.SESSION_USER_ID, 7L))
                .andExpect(status().isOk())
                .andExpect(header().exists(MyAiAgentController.REPORT_ID_HEADER))
                .andReturn().getResponse().getHeader(MyAiAgentController.REPORT_ID_HEADER);

        SavedReport saved = reports.findById(Long.valueOf(id)).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getCompany()).isEqualTo("Airbus");
        assertThat(saved.getMarkdown()).isEqualTo("# Airbus report");
    }

    @Test
    void blankCompanyIsRejectedWithoutCallingModel() throws Exception {
        mockMvc.perform(get("/financialAnalysis").param("company", "   ").sessionAttr(AuthController.SESSION_USER_ID, 1L))
                .andExpect(status().isBadRequest());

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void missingCompanyReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/financialAnalysis").sessionAttr(AuthController.SESSION_USER_ID, 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void financialAnalysisRequiresLogin() throws Exception {
        mockMvc.perform(get("/financialAnalysis").param("company", "Renault"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
