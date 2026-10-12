package com.plmtracker.service;

import com.plmtracker.exception.ResourceNotFoundException;
import com.plmtracker.jira.CreateJiraIssueCommand;
import com.plmtracker.jira.JiraClient;
import com.plmtracker.jira.JiraIntegrationException;
import com.plmtracker.jira.JiraIssue;
import com.plmtracker.model.ChangeRequest;
import com.plmtracker.model.JiraSyncState;
import com.plmtracker.repository.ChangeRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Creates the Jira issue for a change request and records the outcome.
 * <p>
 * Deliberately NOT @Transactional: the HTTP call to Jira can take seconds,
 * and we must not hold a database transaction open while waiting for it.
 * A Jira failure never loses the change request, it only marks it FAILED
 * so it can be retried.
 */
@Service
public class JiraSyncService {

    private static final Logger log = LoggerFactory.getLogger(JiraSyncService.class);

    private final JiraClient jiraClient;
    private final ChangeRequestRepository repository;

    public JiraSyncService(JiraClient jiraClient, ChangeRequestRepository repository) {
        this.jiraClient = jiraClient;
        this.repository = repository;
    }

    /** Tries to create the Jira issue. Updates and saves the given request. Never throws for Jira problems. */
    public void sync(ChangeRequest cr) {
        if (cr.getJiraSyncState() == JiraSyncState.SYNCED) {
            return; // never create a second issue
        }
        try {
            JiraIssue issue = jiraClient.createIssue(new CreateJiraIssueCommand(
                    cr.getPartNumber(),
                    cr.getTitle(),
                    cr.getReason(),
                    cr.getPriority().name(),
                    cr.getCreatedBy()));
            cr.setJiraKey(issue.key());
            cr.setJiraUrl(issue.url());
            cr.setJiraSyncState(JiraSyncState.SYNCED);
            log.info("Change request {} synced to Jira issue {}", cr.getId(), issue.key());
        } catch (JiraIntegrationException e) {
            cr.setJiraSyncState(JiraSyncState.FAILED);
            log.warn("Jira sync failed for change request {}: {}", cr.getId(), e.getMessage());
        }
        repository.save(cr);
    }

    /** Manual retry for one request (used by the API). */
    public ChangeRequest retry(Long id) {
        ChangeRequest cr = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found: " + id));
        sync(cr);
        return cr;
    }

    /** Retries every FAILED request. Returns how many are now SYNCED. */
    public int retryAllFailed() {
        List<ChangeRequest> failed = repository.findByJiraSyncStateOrderByCreatedAtAsc(JiraSyncState.FAILED);
        int synced = 0;
        for (ChangeRequest cr : failed) {
            sync(cr);
            if (cr.getJiraSyncState() == JiraSyncState.SYNCED) {
                synced++;
            }
        }
        return synced;
    }
}
