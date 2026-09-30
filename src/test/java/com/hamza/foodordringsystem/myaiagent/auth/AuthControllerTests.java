package com.hamza.foodordringsystem.myaiagent.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.ai.openai.api-key=test-key", "spring.datasource.url=jdbc:h2:mem:auth-test"})
@AutoConfigureMockMvc
class AuthControllerTests {

    private static final String HAMZA = """
            {"username": "hamza", "email": "Hamza@Example.com", "fullName": "Hamza H", "password": "s3cret-pass"}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppUserRepository users;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void registerCreatesProfileWithHashedPasswordAndLogsIn() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/register").session(session).contentType(MediaType.APPLICATION_JSON).content(HAMZA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("hamza"))
                .andExpect(jsonPath("$.email").value("hamza@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AppUser saved = users.findByUsernameIgnoreCase("hamza").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("s3cret-pass").startsWith("$2");

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Hamza H"));
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(HAMZA))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(HAMZA))
                .andExpect(status().isConflict());
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "hamza", "email": "hamza@example.com", "fullName": "Hamza H", "password": "short"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Password must be at least 8 characters."));
    }

    @Test
    void loginWorksWithUsernameOrEmailAndRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(HAMZA))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"HAMZA\", \"password\": \"s3cret-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("hamza"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"hamza@example.com\", \"password\": \"s3cret-pass\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"hamza\", \"password\": \"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/register").session(session).contentType(MediaType.APPLICATION_JSON).content(HAMZA))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
