package com.plmtracker.service;

import com.plmtracker.exception.ResourceNotFoundException;
import com.plmtracker.jira.CreateJiraIssueCommand;
import com.plmtracker.jira.JiraClient;
import com.plmtracker.jira.JiraIntegrationException;
import com.plmtracker.jira.JiraIssue;
import com.plmtracker.model.ChangeRequest;
import com.plmtracker.model.JiraSyncState;
import com.plmtracker.model.Priority;
import com.plmtracker.repository.ChangeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraSyncServiceTest {

    @Mock
    private JiraClient jiraClient;

    @Mock
    private ChangeRequestRepository repository;

    private JiraSyncService service;

    @BeforeEach
    void setUp() {
        service = new JiraSyncService(jiraClient, repository);
    }

    private ChangeRequest pendingRequest() {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(1L);
        cr.setPartNumber("P-3001");
        cr.setTitle("Stronger bolt");
        cr.setReason("Failed fatigue test");
        cr.setPriority(Priority.HIGH);
        cr.setCreatedBy("Pallavi");
        return cr;
    }

    @Test
    void sync_success_storesKeyAndUrlAndMarksSynced() {
        when(jiraClient.createIssue(any())).thenReturn(new JiraIssue("KAN-12", "https://x/browse/KAN-12"));
        ChangeRequest cr = pendingRequest();

        service.sync(cr);

        assertThat(cr.getJiraSyncState()).isEqualTo(JiraSyncState.SYNCED);
        assertThat(cr.getJiraKey()).isEqualTo("KAN-12");
        assertThat(cr.getJiraUrl()).isEqualTo("https://x/browse/KAN-12");
        verify(repository).save(cr);
    }

    @Test
    void sync_sendsRequestDetailsToJira() {
        when(jiraClient.createIssue(any())).thenReturn(new JiraIssue("KAN-1", "u"));

        service.sync(pendingRequest());

        ArgumentCaptor<CreateJiraIssueCommand> captor = ArgumentCaptor.forClass(CreateJiraIssueCommand.class);
        verify(jiraClient).createIssue(captor.capture());
        assertThat(captor.getValue().partNumber()).isEqualTo("P-3001");
        assertThat(captor.getValue().priority()).isEqualTo("HIGH");
        assertThat(captor.getValue().requestedBy()).isEqualTo("Pallavi");
    }

    @Test
    void sync_jiraFailure_marksFailedAndDoesNotThrow() {
        when(jiraClient.createIssue(any())).thenThrow(new JiraIntegrationException("down", 503));
        ChangeRequest cr = pendingRequest();

        service.sync(cr);

        assertThat(cr.getJiraSyncState()).isEqualTo(JiraSyncState.FAILED);
        assertThat(cr.getJiraKey()).isNull();
        verify(repository).save(cr);
    }

    @Test
    void sync_alreadySynced_neverCreatesSecondIssue() {
        ChangeRequest cr = pendingRequest();
        cr.setJiraSyncState(JiraSyncState.SYNCED);
        cr.setJiraKey("KAN-5");

        service.sync(cr);

        verify(jiraClient, never()).createIssue(any());
        assertThat(cr.getJiraKey()).isEqualTo("KAN-5");
    }

    @Test
    void retry_failedRequest_canBecomeSynced() {
        ChangeRequest cr = pendingRequest();
        cr.setJiraSyncState(JiraSyncState.FAILED);
        when(repository.findById(1L)).thenReturn(Optional.of(cr));
        when(jiraClient.createIssue(any())).thenReturn(new JiraIssue("KAN-7", "u"));

        ChangeRequest result = service.retry(1L);

        assertThat(result.getJiraSyncState()).isEqualTo(JiraSyncState.SYNCED);
        assertThat(result.getJiraKey()).isEqualTo("KAN-7");
    }

    @Test
    void retry_unknownId_throwsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retry(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void retryAllFailed_countsOnlySuccesses() {
        ChangeRequest a = pendingRequest();
        a.setJiraSyncState(JiraSyncState.FAILED);
        ChangeRequest b = pendingRequest();
        b.setId(2L);
        b.setJiraSyncState(JiraSyncState.FAILED);
        when(repository.findByJiraSyncStateOrderByCreatedAtAsc(JiraSyncState.FAILED)).thenReturn(List.of(a, b));
        when(jiraClient.createIssue(any()))
                .thenReturn(new JiraIssue("KAN-8", "u"))
                .thenThrow(new JiraIntegrationException("still down", 503));

        int synced = service.retryAllFailed();

        assertThat(synced).isEqualTo(1);
        assertThat(a.getJiraSyncState()).isEqualTo(JiraSyncState.SYNCED);
        assertThat(b.getJiraSyncState()).isEqualTo(JiraSyncState.FAILED);
        verify(repository, times(2)).save(any(ChangeRequest.class));
    }
}
