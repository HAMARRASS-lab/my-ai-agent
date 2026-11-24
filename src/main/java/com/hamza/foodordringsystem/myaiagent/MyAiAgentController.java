package com.hamza.foodordringsystem.myaiagent;

import com.hamza.foodordringsystem.myaiagent.auth.AuthController;
import com.hamza.foodordringsystem.myaiagent.reports.AnalysisReport;
import com.hamza.foodordringsystem.myaiagent.reports.AnalysisReportRepository;
import com.hamza.foodordringsystem.myaiagent.tools.CompanyFinancials;
import com.hamza.foodordringsystem.myaiagent.tools.CountryIdentityInfo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MyAiAgentController {

    // Lets the frontend link the report it just received to its saved copy.
    public static final String REPORT_ID_HEADER = "X-Report-Id";

    private final ChatClient chatClient;
    private final AnalysisReportRepository reports;

    String systemPrompt = """
    You are a financial analyst assistant.
    Always call both tools before answering:
    - countryIdentityInfo for the name, country, domain and founding year.
    - companyFinancials for real numbers: the live share price (Yahoo Finance) and the latest
      reported revenue, operating income, net profit, market capitalization and employees (Wikidata).
    Only use numbers returned by the tools. Never invent or estimate a figure; if a tool returns
    found=false or a value is missing, say it is unavailable.

    Write the report in markdown with this structure:
    1. A "## <company name>" heading.
    2. Key facts, one per line, each in the exact form "**Label:** value" (no bullet), in this order:
       Country, Domain, Founded, Share price, Revenue, Operating income, Net profit,
       Market capitalization, Employees. Skip any that are unavailable.
       - Share price: price with currency, ticker and exchange, and the day change in percent,
         e.g. "24.68 EUR (RNO.PA, Paris) -2.95% today".
       - Money: compact with currency and fiscal year, e.g. "56.2B EUR (2024)".
    3. "## Financial analysis": scale, profitability (compute the net margin as net profit / revenue
       when both are from the same year), and where the share price sits in its 52-week range.
       If figures use different currencies or years, say so instead of comparing them directly.
    4. "## Conclusion": a concise overall assessment.
    5. A final line: "_Sources: Yahoo Finance (market data), Wikidata (reported figures)._"
    """;

    public MyAiAgentController(ChatClient.Builder chatClient, CountryIdentityInfo countryIdentityInfo,
                               CompanyFinancials companyFinancials, AnalysisReportRepository reports,
                               @Value("${spring.ai.openai.api-key:}") String apiKey) {
        // Spring AI starts fine without a key and only fails on the first request, so fail at startup instead.
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is not set. Copy .env.example to .env, add your key, and start with ./run.sh");
        }
        this.reports = reports;
        this.chatClient = chatClient
                .defaultTools(FunctionToolCallback.builder("countryIdentityInfo", countryIdentityInfo)
                        .description("Get identity information about a company: its name, country, industry domain and founding year")
                        .inputType(CountryIdentityInfo.Request.class)
                        .build(),
                        FunctionToolCallback.builder("companyFinancials", companyFinancials)
                        .description("Get real financial numbers for a company: live share price, day change and 52-week range "
                                + "(Yahoo Finance) plus the latest reported revenue, operating income, net profit, "
                                + "market capitalization and employee count with currency and fiscal year (Wikidata)")
                        .inputType(CompanyFinancials.Request.class)
                        .build())
                .build();
    }

    // Every generated report is saved for the logged-in user (see ReportController).
    @GetMapping(value = "/financialAnalysis", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> askAgent(@RequestParam String company,
                                           @SessionAttribute(AuthController.SESSION_USER_ID) Long userId) {
        String name = company.trim();
        if (name.isEmpty() || name.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company name must be 1-200 characters.");
        }

        long started = System.currentTimeMillis();
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user("Company Name :" + name)
                .call().content();
        if (!StringUtils.hasText(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI assistant returned an empty report. Please try again.");
        }

        AnalysisReport saved = reports.save(new AnalysisReport(userId, name, content, System.currentTimeMillis() - started));
        return ResponseEntity.ok()
                .header(REPORT_ID_HEADER, saved.getId().toString())
                .body(content);
    }
}
