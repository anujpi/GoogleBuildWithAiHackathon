package com.argiintelligence.backend.auth;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registers and logs in users through the real API, so tests exercise the same path as clients. */
public final class AuthTestSupport {

    public static final String PASSWORD = "correct-horse-battery";

    private AuthTestSupport() {
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** Returns the new user's id. */
    public static String register(MockMvc mvc, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Test Farmer", email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    public static String login(MockMvc mvc, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    /** A fresh FARMER account; returns its "Bearer ..." header value. */
    public static String newUserBearer(MockMvc mvc) throws Exception {
        String email = uniqueEmail();
        register(mvc, email);
        return "Bearer " + login(mvc, email);
    }

    public static String registerJson(String fullName, String email, String password) {
        return "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(fullName, email, password);
    }
}
