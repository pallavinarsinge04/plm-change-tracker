package com.plmtracker.jira;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Talks to Jira Cloud REST API v3 using Spring's RestClient. */
@Component
public class RestClientJiraClient implements JiraClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientJiraClient.class);
    private static final int MAX_SUMMARY_LENGTH = 250;
    private static final int MAX_ERROR_TEXT = 300;

    private final JiraProperties props;
    private final RestClient restClient;

    public RestClientJiraClient(JiraProperties props) {
        this.props = props;
        this.restClient = buildRestClient(props);
    }

    private static RestClient buildRestClient(JiraProperties p) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(p.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(p.readTimeout());

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (p.isConfigured()) {
            builder.baseUrl(stripTrailingSlash(p.baseUrl()))
                    .defaultHeaders(h -> h.setBasicAuth(p.email(), p.apiToken()));
        }
        return builder.build();
    }

    @Override
    public JiraIssue createIssue(CreateJiraIssueCommand command) {
        if (!props.isConfigured()) {
            throw new JiraIntegrationException(
                    "Jira is not configured. Set JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN and JIRA_PROJECT_KEY.", null);
        }

        Map<String, Object> body = buildBody(command);
        log.info("Creating Jira issue in project {} for part {}", props.projectKey(), command.partNumber());

        try {
            CreateResponse response = restClient.post()
                    .uri("/rest/api/3/issue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        throw toException(res);
                    })
                    .body(CreateResponse.class);

            if (response == null || response.key() == null) {
                throw new JiraIntegrationException("Jira returned an empty response", null);
            }
            log.info("Created Jira issue {}", response.key());
            return new JiraIssue(response.key(), browseUrl(response.key()));

        } catch (JiraIntegrationException e) {
            log.warn("Jira call failed: {}", e.getMessage());
            throw e;
        } catch (RestClientException e) {
            log.warn("Could not reach Jira: {}", e.getMessage());
            throw new JiraIntegrationException("Could not reach Jira: " + e.getMessage(), null, e);
        }
    }

    // ----- request building -----

    private Map<String, Object> buildBody(CreateJiraIssueCommand c) {
        String summary = "[" + c.partNumber() + "] " + c.title();
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            summary = summary.substring(0, MAX_SUMMARY_LENGTH);
        }

        Map<String, Object> description = Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(
                        paragraph("Part number: " + c.partNumber()),
                        paragraph("Priority: " + c.priority()),
                        paragraph("Requested by: " + c.requestedBy()),
                        paragraph("Reason: " + c.reason())
                ));

        Map<String, Object> fields = Map.of(
                "project", Map.of("key", props.projectKey()),
                "summary", summary,
                "issuetype", Map.of("name", props.issueType()),
                "description", description
        );
        return Map.of("fields", fields);
    }

    /** Jira API v3 wants descriptions in Atlassian Document Format, not plain text. */
    private static Map<String, Object> paragraph(String text) {
        return Map.of(
                "type", "paragraph",
                "content", List.of(Map.of("type", "text", "text", text)));
    }

    // ----- error handling -----

    private static JiraIntegrationException toException(ClientHttpResponse res) throws IOException {
        int status = res.getStatusCode().value();
        String text = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
        if (text.length() > MAX_ERROR_TEXT) {
            text = text.substring(0, MAX_ERROR_TEXT) + "...";
        }
        return new JiraIntegrationException(
                "Jira rejected the request (HTTP " + status + "): " + text, status);
    }

    private String browseUrl(String key) {
        return stripTrailingSlash(props.baseUrl()) + "/browse/" + key;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Only the fields we need from Jira's 201 response. */
    public record CreateResponse(String id, String key, String self) {
    }
}
