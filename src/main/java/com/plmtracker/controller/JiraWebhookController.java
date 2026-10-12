package com.plmtracker.controller;

import com.plmtracker.jira.JiraWebhookPayload;
import com.plmtracker.service.JiraWebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Receives Jira webhooks. Jira cannot log in, so the URL carries a shared secret:
 * POST /api/webhooks/jira?token=...
 * If no secret is configured the endpoint rejects everything (fails closed).
 */
@RestController
@RequestMapping("/api/webhooks")
public class JiraWebhookController {

    private final JiraWebhookService service;
    private final String secret;

    public JiraWebhookController(JiraWebhookService service,
                                 @Value("${app.jira.webhook-secret:}") String secret) {
        this.service = service;
        this.secret = secret;
    }

    @PostMapping("/jira")
    public ResponseEntity<Map<String, String>> receive(
            @RequestParam(name = "token", required = false) String token,
            @RequestBody JiraWebhookPayload payload) {

        if (!tokenIsValid(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or missing webhook token"));
        }
        JiraWebhookService.Result result = service.handle(payload);
        // Always 200 for a valid token, so Jira does not keep retrying events we ignore.
        return ResponseEntity.ok(Map.of("result", result.name()));
    }

    private boolean tokenIsValid(String token) {
        if (secret == null || secret.isBlank() || token == null) {
            return false;
        }
        // constant-time comparison
        return MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
