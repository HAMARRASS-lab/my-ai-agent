package com.hamza.foodordringsystem.myaiagent;

import com.hamza.foodordringsystem.myaiagent.tools.CountryIdentityInfo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyAiAgentController {

    private final ChatClient chatClient;

    String systemPrompt = """
    You are a helpful assistant.
    Always call the countryIdentityInfo tool first and use its data for the name, country, domain and founding year.
    If the tool returns found=false or a field is empty, say it is unknown instead of guessing.
    Your report should be in markdown format (name, country, domain).
    Your report should include a concise conclusion about the financial analyse.
    """;

    public MyAiAgentController(ChatClient.Builder chatClient, CountryIdentityInfo countryIdentityInfo,
                               @Value("${spring.ai.openai.api-key:}") String apiKey) {
        // Spring AI starts fine without a key and only fails on the first request, so fail at startup instead.
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is not set. Copy .env.example to .env, add your key, and start with ./run.sh");
        }
        this.chatClient = chatClient
                .defaultToolCallbacks(FunctionToolCallback.builder("countryIdentityInfo", countryIdentityInfo)
                        .description("Get identity information about a company: its name, country, industry domain and founding year")
                        .inputType(CountryIdentityInfo.Request.class)
                        .build())
                .build();
    }
//

    @GetMapping(value = "/financialAnalysis", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public String askAgent(@RequestParam String company) {
        System.out.println("askAgent");

        return chatClient.prompt()
                .system(systemPrompt)
                .user("Company Name :" + company)
                .call().content();
    }


}
