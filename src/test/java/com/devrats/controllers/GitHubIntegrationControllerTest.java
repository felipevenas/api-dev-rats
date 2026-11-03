package com.devrats.controllers;

import com.devrats.services.GitHubIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubIntegrationControllerTest {

    private GitHubIntegrationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(GitHubIntegrationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new GitHubIntegrationController(service)).build();
    }

    @Test
    void shouldRedirectToFrontend_whenGithubCallbackReturnsError() throws Exception {
        when(service.handleCallback(null, null, "access_denied"))
                .thenReturn("http://localhost:5173?github=error&reason=access_denied");

        mockMvc.perform(get("/api/v1/integrations/github/callback").param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173?github=error&reason=access_denied"));
    }

    @Test
    void shouldRedirectToFrontend_whenSetupComesWithoutInstallationId() throws Exception {
        when(service.handleSetup(null, null, null, null))
                .thenReturn("http://localhost:5173?github=error&reason=invalid_request");

        mockMvc.perform(get("/api/v1/integrations/github/setup"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173?github=error&reason=invalid_request"));
    }
}
