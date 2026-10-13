package com.plmtracker.security;

import com.plmtracker.controller.ChangeRequestController;
import com.plmtracker.controller.JiraWebhookController;
import com.plmtracker.dto.CreateChangeRequestRequest;
import com.plmtracker.model.ChangeRequest;
import com.plmtracker.model.Priority;
import com.plmtracker.service.ChangeRequestService;
import com.plmtracker.service.JiraSyncService;
import com.plmtracker.service.JiraWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the access rules with the real security configuration and the real controllers,
 * but with mocked services. No database, no Jira, no Mini PLM.
 */
@SpringJUnitWebConfig
@TestPropertySource(properties = {
        "app.security.engineer-password=engineer-password-123",
        "app.security.viewer-password=viewer-password-1234",
        "app.jira.webhook-secret=webhook-secret-value"
})
class SecurityRulesTest {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, ChangeRequestController.class, JiraWebhookController.class})
    static class TestConfig {
    }

    @MockitoBean
    ChangeRequestService changeRequestService;

    @MockitoBean
    JiraSyncService jiraSyncService;

    @MockitoBean
    JiraWebhookService jiraWebhookService;

    private MockMvc mvc;

    private static final String CREATE_BODY =
            "{\"partNumber\":\"P-1\",\"title\":\"t\",\"reason\":\"r\",\"createdBy\":\"someone-else\"}";
    private static final String UPDATE_BODY =
            "{\"title\":\"t\",\"reason\":\"r\",\"priority\":\"LOW\"}";

    @BeforeEach
    void setUp(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        ChangeRequest sample = new ChangeRequest();
        sample.setId(1L);
        sample.setPartNumber("P-1");
        sample.setTitle("t");
        sample.setReason("r");
        sample.setPriority(Priority.LOW);
        sample.setCreatedBy("engineer");
        when(changeRequestService.get(1L)).thenReturn(sample);
        when(changeRequestService.create(any())).thenReturn(sample);
        when(changeRequestService.update(any(), any())).thenReturn(sample);
        when(jiraWebhookService.handle(any())).thenReturn(JiraWebhookService.Result.UNCHANGED);
    }

    // ----- reading -----

    @Test
    void noLogin_isUnauthorized() throws Exception {
        mvc.perform(get("/api/change-requests")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPassword_isUnauthorized() throws Exception {
        mvc.perform(get("/api/change-requests").with(httpBasic("engineer", "wrong-password-xx")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewer_canRead() throws Exception {
        mvc.perform(get("/api/change-requests").with(httpBasic("viewer", "viewer-password-1234")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/change-requests/1").with(httpBasic("viewer", "viewer-password-1234")))
                .andExpect(status().isOk());
    }

    // ----- writing -----

    @Test
    void viewer_cannotCreateOrUpdate() throws Exception {
        mvc.perform(post("/api/change-requests").with(httpBasic("viewer", "viewer-password-1234"))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/change-requests/1").with(httpBasic("viewer", "viewer-password-1234"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/change-requests/1/jira-sync").with(httpBasic("viewer", "viewer-password-1234")))
                .andExpect(status().isForbidden());

        verify(changeRequestService, never()).create(any());
    }

    @Test
    void engineer_canCreate_andAuthorIsTheLoggedInUser_notTheBody() throws Exception {
        mvc.perform(post("/api/change-requests").with(httpBasic("engineer", "engineer-password-123"))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateChangeRequestRequest> captor =
                ArgumentCaptor.forClass(CreateChangeRequestRequest.class);
        verify(changeRequestService).create(captor.capture());
        assertThat(captor.getValue().createdBy()).isEqualTo("engineer");
    }

    @Test
    void engineer_canUpdate() throws Exception {
        mvc.perform(put("/api/change-requests/1").with(httpBasic("engineer", "engineer-password-123"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk());
    }

    // ----- webhook and everything else -----

    @Test
    void webhook_needsNoLogin_butNeedsItsOwnToken() throws Exception {
        String body = "{\"webhookEvent\":\"jira:issue_updated\"}";

        mvc.perform(post("/api/webhooks/jira?token=webhook-secret-value")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(post("/api/webhooks/jira?token=wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/webhooks/jira")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPaths_areDenied_evenWhenLoggedIn() throws Exception {
        mvc.perform(get("/api/anything-else").with(httpBasic("engineer", "engineer-password-123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwords_areNeverStoredInPlainText() {
        var encoder = new SecurityConfig().passwordEncoder();
        String stored = encoder.encode("engineer-password-123");

        assertThat(stored).doesNotContain("engineer-password-123");
        assertThat(encoder.matches("engineer-password-123", stored)).isTrue();
    }
}
