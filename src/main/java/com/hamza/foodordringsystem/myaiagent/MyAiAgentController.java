package com.hamza.foodordringsystem.myaiagent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.*;

@RestController
public class MyAiAgentController {

    private ChatClient chatClient;

    String systemPrompt = """
    You are a helpful assistant.
    Your report should be in markdown format (name, country, domain).
    Your report should include a concise conclusion about the financial analyse.
    """;

    public MyAiAgentController(ChatClient.Builder chatClient) {
        this.chatClient = chatClient.build();
    }
//

    @GetMapping(value = "/financialAnalysis" , produces = MediaType.TEXT_MARKDOWN_VALUE)
    public String askAgent(String company){
        System.out.println("askAgent");

        return chatClient.prompt()
                .system(systemPrompt)
                .user("Company Name :" + company)
                .call().content();
    }


}
