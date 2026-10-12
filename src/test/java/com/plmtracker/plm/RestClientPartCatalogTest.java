package com.plmtracker.plm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests the Mini PLM client against a tiny fake Mini PLM started inside the test. */
class RestClientPartCatalogTest {

    private static final String LOGIN_OK =
            "{\"token\":\"jwt-1\",\"tokenType\":\"Bearer\",\"expiresInSeconds\":7200,"
                    + "\"email\":\"svc@plm.com\",\"name\":\"Svc\",\"role\":\"DESIGNER\"}";

    private static final String PARTS_JSON = """
            [
              {"id":1,"partNumber":"P-3001","name":"Bolt","description":"x","type":"COMPONENT",
               "latestRevision":"A","latestState":"RELEASED"},
              {"id":2,"partNumber":"P-30010","name":"Long bolt","description":null,"type":"COMPONENT",
               "latestRevision":"A","latestState":"IN_WORK"}
            ]
            """;

    private HttpServer server;
    private final AtomicInteger loginCalls = new AtomicInteger();
    private final AtomicInteger partsCalls = new AtomicInteger();
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> loginBody = new AtomicReference<>();

    // behaviour switches for the fake server
    private volatile int loginStatus = 200;
    private volatile int firstPartsStatus = 200;   // status for the very first /api/parts call
    private volatile long partsDelayMillis = 0;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startFakeMiniPlm() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/login", ex -> {
            loginCalls.incrementAndGet();
            loginBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (loginStatus == 200) {
                // each login hands out a different token, so tests can see a re-login happened
                respond(ex, 200, LOGIN_OK.replace("jwt-1", "jwt-" + loginCalls.get()));
            } else {
                respond(ex, loginStatus, "{\"message\":\"bad credentials\"}");
            }
        });
        server.createContext("/api/parts", ex -> {
            int call = partsCalls.incrementAndGet();
            lastAuthHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
            lastQuery.set(ex.getRequestURI().getRawQuery());
            if (partsDelayMillis > 0) {
                try {
                    Thread.sleep(partsDelayMillis);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            if (call == 1 && firstPartsStatus != 200) {
                respond(ex, firstPartsStatus, "{}");
            } else {
                respond(ex, 200, PARTS_JSON);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange ex, int status, String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // client may have given up (timeout test)
        } finally {
            ex.close();
        }
    }

    private RestClientPartCatalog catalog(String baseUrl, Duration readTimeout) {
        return new RestClientPartCatalog(new MiniPlmProperties(
                baseUrl, "svc@plm.com", "pw", Duration.ofSeconds(2), readTimeout));
    }

    @Test
    void findsPartByExactNumber_andIgnoresPartialMatches() throws Exception {
        var catalog = catalog(startFakeMiniPlm(), Duration.ofSeconds(3));

        Optional<PartInfo> part = catalog.findByPartNumber("p-3001");

        assertThat(part).isPresent();
        assertThat(part.get().partNumber()).isEqualTo("P-3001");
        assertThat(part.get().latestState()).isEqualTo("RELEASED");
        assertThat(lastQuery.get()).contains("q=p-3001");
    }

    @Test
    void unknownPart_returnsEmpty() throws Exception {
        var catalog = catalog(startFakeMiniPlm(), Duration.ofSeconds(3));

        assertThat(catalog.findByPartNumber("P-0000")).isEmpty();
    }

    @Test
    void logsInWithServiceAccount_andSendsBearerToken() throws Exception {
        var catalog = catalog(startFakeMiniPlm(), Duration.ofSeconds(3));

        catalog.findByPartNumber("P-3001");

        assertThat(loginBody.get()).contains("svc@plm.com").contains("pw");
        assertThat(lastAuthHeader.get()).isEqualTo("Bearer jwt-1");
    }

    @Test
    void tokenIsReused_notLoginPerLookup() throws Exception {
        var catalog = catalog(startFakeMiniPlm(), Duration.ofSeconds(3));

        catalog.findByPartNumber("P-3001");
        catalog.findByPartNumber("P-3001");

        assertThat(loginCalls.get()).isEqualTo(1);
        assertThat(partsCalls.get()).isEqualTo(2);
    }

    @Test
    void expiredToken_401_triggersOneReLoginAndRetry() throws Exception {
        firstPartsStatus = 401;
        var catalog = catalog(startFakeMiniPlm(), Duration.ofSeconds(3));

        Optional<PartInfo> part = catalog.findByPartNumber("P-3001");

        assertThat(part).isPresent();
        assertThat(loginCalls.get()).isEqualTo(2);
        assertThat(lastAuthHeader.get()).isEqualTo("Bearer jwt-2");
    }

    @Test
    void wrongServiceAccount_throwsCatalogException() throws Exception {
        loginStatus = 401;
        var catalog = catalog(startFakeMiniPlm(), Duration.ofSeconds(3));

        assertThatThrownBy(() -> catalog.findByPartNumber("P-3001"))
                .isInstanceOf(PartCatalogException.class)
                .hasMessageContaining("login failed");
    }

    @Test
    void miniPlmServerError_throwsCatalogException() throws Exception {
        firstPartsStatus = 500;
        var catalog = catalog(startFakeMiniPlm(), Duration.ofSeconds(3));

        assertThatThrownBy(() -> catalog.findByPartNumber("P-3001"))
                .isInstanceOf(PartCatalogException.class)
                .hasMessageContaining("500");
    }

    @Test
    void slowMiniPlm_timesOutAsCatalogException() throws Exception {
        partsDelayMillis = 1500;
        var catalog = catalog(startFakeMiniPlm(), Duration.ofMillis(300));

        assertThatThrownBy(() -> catalog.findByPartNumber("P-3001"))
                .isInstanceOf(PartCatalogException.class);
    }

    @Test
    void notConfigured_isDisabled_andPasswordIsHidden() {
        var props = new MiniPlmProperties("", "", "", null, null);
        var catalog = new RestClientPartCatalog(props);

        assertThat(catalog.isEnabled()).isFalse();
        assertThat(new MiniPlmProperties("http://x", "a@b.c", "topsecret", null, null).toString())
                .doesNotContain("topsecret");
    }
}
