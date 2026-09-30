package com.hamza.foodordringsystem.myaiagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.ai.openai.api-key=test-key", "spring.datasource.url=jdbc:h2:mem:test"})
class MyAiAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
