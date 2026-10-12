package com.plmtracker.jira;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the Jira client against a tiny fake Jira server started inside the test.
 * No real Jira call is made and no extra library is needed.
 */
class RestClientJiraClientTest {

    private static final String EMAIL = "me@example.com";
    private static final String TOKEN = "secret-token";

    private HttpServer server;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startFakeJira(int status, String responseJson, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastPath.set(exchange.getRequestURI().getPath());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // the client may already have given up (timeout test)
            } finally {
                exchange.close();
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private RestClientJiraClient clientFor(String baseUrl, Duration readTimeout) {
        return new RestClientJiraClient(new JiraProperties(
                baseUrl, EMAIL, TOKEN, "KAN", "Task", Duration.ofSeconds(2), readTimeout));
    }

    private CreateJiraIssueCommand sampleCommand() {
        return new CreateJiraIssueCommand(
                "P-3001", "Use stronger bolt", "Failed fatigue test", "HIGH", "Pallavi");
    }

    @Test
    void createIssue_success_returnsKeyAndBrowseUrl() throws Exception {
        String base = startFakeJira(201, "{\"id\":\"10014\",\"key\":\"KAN-5\",\"self\":\"http://x/10014\"}", 0);

        JiraIssue issue = clientFor(base, Duration.ofSeconds(5)).createIssue(sampleCommand());

        assertThat(issue.key()).isEqualTo("KAN-5");
        assertThat(issue.url()).isEqualTo(base + "/browse/KAN-5");
    }

    @Test
    void createIssue_sendsBasicAuthAndExpectedBody() throws Exception {
        String base = startFakeJira(201, "{\"id\":\"1\",\"key\":\"KAN-1\",\"self\":\"s\"}", 0);

        clientFor(base, Duration.ofSeconds(5)).createIssue(sampleCommand());

        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString((EMAIL + ":" + TOKEN).getBytes(StandardCharsets.UTF_8));
        assertThat(lastAuth.get()).isEqualTo(expectedAuth);
        assertThat(lastPath.get()).isEqualTo("/rest/api/3/issue");
        assertThat(lastBody.get())
                .contains("\"key\":\"KAN\"")
                .contains("[P-3001] Use stronger bolt")
                .contains("\"name\":\"Task\"")
                .contains("\"type\":\"doc\"")
                .doesNotContain(TOKEN);
    }

    @Test
    void createIssue_unauthorized_throwsWithStatus401() throws Exception {
        String base = startFakeJira(401, "{\"message\":\"Client must be authenticated\"}", 0);

        assertThatThrownBy(() -> clientFor(base, Duration.ofSeconds(5)).createIssue(sampleCommand()))
                .isInstanceOf(JiraIntegrationException.class)
                .hasMessageContaining("401")
                .satisfies(e -> assertThat(((JiraIntegrationException) e).getUpstreamStatus()).isEqualTo(401));
    }

    @Test
    void createIssue_badRequest_includesJiraErrorText() throws Exception {
        String base = startFakeJira(400,
                "{\"errorMessages\":[],\"errors\":{\"project\":\"The target project doesn't exist\"}}", 0);

        assertThatThrownBy(() -> clientFor(base, Duration.ofSeconds(5)).createIssue(sampleCommand()))
                .isInstanceOf(JiraIntegrationException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("target project");
    }

    @Test
    void createIssue_serverError_throwsWithStatus500() throws Exception {
        String base = startFakeJira(500, "{\"message\":\"boom\"}", 0);

        assertThatThrownBy(() -> clientFor(base, Duration.ofSeconds(5)).createIssue(sampleCommand()))
                .isInstanceOf(JiraIntegrationException.class)
                .satisfies(e -> assertThat(((JiraIntegrationException) e).getUpstreamStatus()).isEqualTo(500));
    }

    @Test
    void createIssue_slowJira_timesOut() throws Exception {
        String base = startFakeJira(201, "{\"id\":\"1\",\"key\":\"KAN-9\",\"self\":\"s\"}", 1500);

        assertThatThrownBy(() -> clientFor(base, Duration.ofMillis(300)).createIssue(sampleCommand()))
                .isInstanceOf(JiraIntegrationException.class)
                .hasMessageContaining("Could not reach Jira")
                .satisfies(e -> assertThat(((JiraIntegrationException) e).getUpstreamStatus()).isNull());
    }

    @Test
    void createIssue_notConfigured_failsClearly() {
        RestClientJiraClient client = new RestClientJiraClient(new JiraProperties(
                "", "", "", "", null, null, null));

        assertThatThrownBy(() -> client.createIssue(sampleCommand()))
                .isInstanceOf(JiraIntegrationException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void properties_toString_hidesToken() {
        JiraProperties props = new JiraProperties("https://x.atlassian.net", EMAIL, TOKEN, "KAN", "Task", null, null);

        assertThat(props.toString()).doesNotContain(TOKEN);
    }
}
