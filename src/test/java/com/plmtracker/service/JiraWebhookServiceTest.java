package com.plmtracker.service;

import com.plmtracker.jira.JiraWebhookPayload;
import com.plmtracker.model.ChangeRequest;
import com.plmtracker.model.ChangeRequestStatus;
import com.plmtracker.repository.ChangeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraWebhookServiceTest {

    @Mock
    private ChangeRequestRepository repository;

    private JiraWebhookService service;

    @BeforeEach
    void setUp() {
        service = new JiraWebhookService(repository);
    }

    private static JiraWebhookPayload payload(String key, String category) {
        return new JiraWebhookPayload("jira:issue_updated",
                new JiraWebhookPayload.Issue(key,
                        new JiraWebhookPayload.Fields(
                                new JiraWebhookPayload.Status("whatever",
                                        category == null ? null : new JiraWebhookPayload.StatusCategory(category)))));
    }

    private ChangeRequest existing(ChangeRequestStatus status) {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setJiraKey("KAN-12");
        cr.setStatus(status);
        when(repository.findByJiraKey("KAN-12")).thenReturn(Optional.of(cr));
        return cr;
    }

    @Test
    void indeterminate_movesOpenToInReview() {
        ChangeRequest cr = existing(ChangeRequestStatus.OPEN);

        var result = service.handle(payload("KAN-12", "indeterminate"));

        assertThat(result).isEqualTo(JiraWebhookService.Result.UPDATED);
        assertThat(cr.getStatus()).isEqualTo(ChangeRequestStatus.IN_REVIEW);
        verify(repository).save(cr);
    }

    @Test
    void done_closesTheRequest() {
        ChangeRequest cr = existing(ChangeRequestStatus.IN_REVIEW);

        service.handle(payload("KAN-12", "done"));

        assertThat(cr.getStatus()).isEqualTo(ChangeRequestStatus.CLOSED);
    }

    @Test
    void new_reopensTheRequest() {
        ChangeRequest cr = existing(ChangeRequestStatus.CLOSED);

        service.handle(payload("KAN-12", "new"));

        assertThat(cr.getStatus()).isEqualTo(ChangeRequestStatus.OPEN);
    }

    @Test
    void sameStatusTwice_isUnchangedAndNotSaved() {
        existing(ChangeRequestStatus.IN_REVIEW);

        var result = service.handle(payload("KAN-12", "indeterminate"));

        assertThat(result).isEqualTo(JiraWebhookService.Result.UNCHANGED);
        verify(repository, never()).save(any(ChangeRequest.class));
    }

    @Test
    void unknownIssueKey_isIgnored() {
        when(repository.findByJiraKey("KAN-99")).thenReturn(Optional.empty());

        assertThat(service.handle(payload("KAN-99", "done"))).isEqualTo(JiraWebhookService.Result.IGNORED);
        verify(repository, never()).save(any(ChangeRequest.class));
    }

    @Test
    void unknownCategory_isIgnored() {
        existing(ChangeRequestStatus.OPEN);

        assertThat(service.handle(payload("KAN-12", "something-new"))).isEqualTo(JiraWebhookService.Result.IGNORED);
        assertThat(service.handle(payload("KAN-12", null))).isEqualTo(JiraWebhookService.Result.IGNORED);
    }

    @Test
    void payloadWithoutIssue_isIgnored() {
        assertThat(service.handle(new JiraWebhookPayload("jira:issue_updated", null)))
                .isEqualTo(JiraWebhookService.Result.IGNORED);
        assertThat(service.handle(null)).isEqualTo(JiraWebhookService.Result.IGNORED);
    }
}
