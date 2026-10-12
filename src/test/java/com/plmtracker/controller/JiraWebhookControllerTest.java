package com.plmtracker.controller;

import com.plmtracker.jira.JiraWebhookPayload;
import com.plmtracker.service.JiraWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JiraWebhookControllerTest {

    private final JiraWebhookService service = mock(JiraWebhookService.class);
    private final JiraWebhookPayload payload = new JiraWebhookPayload("jira:issue_updated", null);

    @Test
    void validToken_callsServiceAndReturns200() {
        when(service.handle(payload)).thenReturn(JiraWebhookService.Result.UPDATED);
        var controller = new JiraWebhookController(service, "s3cret");

        ResponseEntity<Map<String, String>> response = controller.receive("s3cret", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("result", "UPDATED");
    }

    @Test
    void wrongToken_isRejectedWithoutTouchingService() {
        var controller = new JiraWebhookController(service, "s3cret");

        assertThat(controller.receive("nope", payload).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.receive(null, payload).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(service, never()).handle(any());
    }

    @Test
    void noSecretConfigured_rejectsEverything() {
        var controller = new JiraWebhookController(service, "");

        assertThat(controller.receive("", payload).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.receive("anything", payload).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(service, never()).handle(any());
    }
}
