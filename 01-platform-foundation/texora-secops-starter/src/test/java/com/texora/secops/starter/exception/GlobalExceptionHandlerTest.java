package com.texora.secops.starter.exception;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.texora.secops.starter.config.StarterAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
//NEW
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

//texora-secops-starter is a shared library, not a runnable app — it has no
//@SpringBootApplication class for @WebMvcTest to auto-detect. We tell it
//exactly which classes to load instead of relying on auto-detection.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@ContextConfiguration(classes = {
 GlobalExceptionHandlerTest.TestController.class,
 StarterAutoConfiguration.class
})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Fixture controller ──────────────────────────────────────────────────

    @RestController
    static class TestController {
        @GetMapping("/test/forbidden")
        public void forbidden() {
            throw PlatformException.ForbiddenException.rbacDenied("Caller lacks required role");
        }

        @GetMapping("/test/notfound")
        public void notFound() {
            throw PlatformException.NotFoundException.forResource("Widget", "abc-123");
        }

        @GetMapping("/test/conflict")
        public void conflict() {
            throw new PlatformException.ConflictException("DUPLICATE_KEY", "Already exists");
        }

        @GetMapping("/test/unprocessable")
        public void unprocessable() {
            throw new PlatformException.UnprocessableException("RULE_VIOLATION", "Two-person rule violated");
        }

        @GetMapping("/test/unexpected")
        public void unexpected() {
            throw new RuntimeException("Something exploded internally");
        }

        @GetMapping("/test/unauthenticated")
        public void unauthenticated() {
            throw new PlatformException.UnauthenticatedException("MISSING_TOKEN", "No token provided");
        }
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    void forbidden_returns_403_with_RBAC_DENIED_code() throws Exception {
        mockMvc.perform(get("/test/forbidden").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("RBAC_DENIED"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void not_found_returns_404() throws Exception {
        mockMvc.perform(get("/test/notfound").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void conflict_returns_409() throws Exception {
        mockMvc.perform(get("/test/conflict").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));
    }

    @Test
    void unprocessable_returns_422() throws Exception {
        mockMvc.perform(get("/test/unprocessable").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.code").value("RULE_VIOLATION"));
    }

    @Test
    void unauthenticated_returns_401() throws Exception {
        mockMvc.perform(get("/test/unauthenticated").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("MISSING_TOKEN"));
    }

    @Test
    void unexpected_exception_returns_500_with_no_internal_detail() throws Exception {
        mockMvc.perform(get("/test/unexpected").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            // Internal exception message must NOT be leaked
            .andExpect(jsonPath("$.message").doesNotExist());
        // (message exists but should not contain "exploded" — check code only)
    }

    @Test
    void error_body_always_contains_timestamp_status_code_message() throws Exception {
        mockMvc.perform(get("/test/forbidden").accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }
}
