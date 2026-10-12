package com.plmtracker.service;

import com.plmtracker.jira.JiraWebhookPayload;
import com.plmtracker.model.ChangeRequest;
import com.plmtracker.model.ChangeRequestStatus;
import com.plmtracker.repository.ChangeRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Applies a Jira status change to the matching change request.
 * <p>
 * Mapping uses Jira's status CATEGORY, not the status name, so it still works
 * if someone renames "In Progress" on the board:
 * new = OPEN, indeterminate = IN_REVIEW, done = CLOSED.
 * <p>
 * Safe to receive the same event twice (idempotent), and events for issues
 * we don't know about are ignored.
 */
@Service
public class JiraWebhookService {

    public enum Result { UPDATED, UNCHANGED, IGNORED }

    private static final Logger log = LoggerFactory.getLogger(JiraWebhookService.class);

    private final ChangeRequestRepository repository;

    public JiraWebhookService(ChangeRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Result handle(JiraWebhookPayload payload) {
        String key = payload == null ? null : payload.issueKey();
        if (key == null || key.isBlank()) {
            log.debug("Jira webhook ignored: no issue key");
            return Result.IGNORED;
        }

        Optional<ChangeRequestStatus> mapped = mapCategory(payload.statusCategoryKey());
        if (mapped.isEmpty()) {
            log.debug("Jira webhook ignored for {}: unknown status category", key);
            return Result.IGNORED;
        }

        Optional<ChangeRequest> found = repository.findByJiraKey(key);
        if (found.isEmpty()) {
            log.debug("Jira webhook ignored: no change request for {}", key);
            return Result.IGNORED;
        }

        ChangeRequest cr = found.get();
        if (cr.getStatus() == mapped.get()) {
            return Result.UNCHANGED;
        }
        log.info("Change request {} ({}): {} -> {}", cr.getId(), key, cr.getStatus(), mapped.get());
        cr.setStatus(mapped.get());
        repository.save(cr);
        return Result.UPDATED;
    }

    static Optional<ChangeRequestStatus> mapCategory(String categoryKey) {
        if (categoryKey == null) {
            return Optional.empty();
        }
        return switch (categoryKey) {
            case "new" -> Optional.of(ChangeRequestStatus.OPEN);
            case "indeterminate" -> Optional.of(ChangeRequestStatus.IN_REVIEW);
            case "done" -> Optional.of(ChangeRequestStatus.CLOSED);
            default -> Optional.empty();
        };
    }
}
