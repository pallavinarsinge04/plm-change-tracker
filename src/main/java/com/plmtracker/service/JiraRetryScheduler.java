package com.plmtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically retries change requests whose Jira issue could not be created. */
@Component
public class JiraRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(JiraRetryScheduler.class);

    private final JiraSyncService syncService;

    public JiraRetryScheduler(JiraSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(
            initialDelayString = "${app.jira.retry-interval:60s}",
            fixedDelayString = "${app.jira.retry-interval:60s}")
    public void retryFailed() {
        int synced = syncService.retryAllFailed();
        if (synced > 0) {
            log.info("Jira retry job synced {} change request(s)", synced);
        }
    }
}
