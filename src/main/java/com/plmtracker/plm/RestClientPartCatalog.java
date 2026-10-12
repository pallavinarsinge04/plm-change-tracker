package com.plmtracker.plm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Asks Mini PLM whether a part number exists. Logs in once with the service
 * account, keeps the JWT until shortly before it expires, and logs in again
 * if Mini PLM answers 401.
 */
@Component
public class RestClientPartCatalog implements PartCatalog {

    private static final Logger log = LoggerFactory.getLogger(RestClientPartCatalog.class);
    private static final long EXPIRY_SAFETY_SECONDS = 60;

    private final MiniPlmProperties props;
    private final RestClient restClient;

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public RestClientPartCatalog(MiniPlmProperties props) {
        this.props = props;
        this.restClient = buildRestClient(props);
    }

    private static RestClient buildRestClient(MiniPlmProperties p) {
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
            builder.baseUrl(stripTrailingSlash(p.baseUrl()));
        }
        return builder.build();
    }

    @Override
    public boolean isEnabled() {
        return props.isConfigured();
    }

    @Override
    public Optional<PartInfo> findByPartNumber(String partNumber) {
        if (!isEnabled()) {
            throw new PartCatalogException("Mini PLM is not configured");
        }
        try {
            List<MiniPlmPart> found = search(partNumber, false);
            return pickExact(found, partNumber);
        } catch (UnauthorizedException e) {
            // token expired or was rejected: log in again once and retry
            invalidateToken();
            try {
                return pickExact(search(partNumber, true), partNumber);
            } catch (UnauthorizedException again) {
                throw new PartCatalogException("Mini PLM rejected the service account credentials");
            } catch (RestClientException ex) {
                throw unreachable(ex);
            }
        } catch (RestClientException e) {
            throw unreachable(e);
        }
    }

    // ----- calls to Mini PLM -----

    private List<MiniPlmPart> search(String partNumber, boolean forceLogin) {
        String jwt = currentToken(forceLogin);
        List<MiniPlmPart> result = restClient.get()
                .uri(b -> b.path("/api/parts").queryParam("q", partNumber).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    int status = res.getStatusCode().value();
                    if (status == 401) {
                        throw new UnauthorizedException();
                    }
                    throw new PartCatalogException("Mini PLM answered HTTP " + status);
                })
                .body(new ParameterizedTypeReference<List<MiniPlmPart>>() {
                });
        return result == null ? List.of() : result;
    }

    private synchronized String currentToken(boolean forceLogin) {
        if (forceLogin || token == null || Instant.now().isAfter(tokenExpiresAt)) {
            login();
        }
        return token;
    }

    private synchronized void invalidateToken() {
        token = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    private void login() {
        log.info("Logging in to Mini PLM as {}", props.email());
        LoginResponse response = restClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", props.email(), "password", props.password()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    throw new PartCatalogException(
                            "Mini PLM login failed (HTTP " + res.getStatusCode().value() + ")");
                })
                .body(LoginResponse.class);
        if (response == null || response.token() == null) {
            throw new PartCatalogException("Mini PLM login returned no token");
        }
        token = response.token();
        long seconds = Math.max(0, response.expiresInSeconds() - EXPIRY_SAFETY_SECONDS);
        tokenExpiresAt = Instant.now().plusSeconds(seconds);
    }

    // ----- helpers -----

    /** Mini PLM search is "contains", so P-30 also returns P-3001. We want the exact match only. */
    private static Optional<PartInfo> pickExact(List<MiniPlmPart> parts, String partNumber) {
        String wanted = partNumber.trim().toUpperCase(Locale.ROOT);
        return parts.stream()
                .filter(p -> p.partNumber() != null
                        && p.partNumber().trim().toUpperCase(Locale.ROOT).equals(wanted))
                .findFirst()
                .map(p -> new PartInfo(p.partNumber(), p.name(), p.latestRevision(), p.latestState()));
    }

    private PartCatalogException unreachable(RestClientException e) {
        log.warn("Could not reach Mini PLM: {}", e.getMessage());
        return new PartCatalogException("Could not reach Mini PLM: " + e.getMessage(), e);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Internal signal: Mini PLM said 401. */
    private static class UnauthorizedException extends RuntimeException {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MiniPlmPart(String partNumber, String name, String latestRevision, String latestState) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LoginResponse(String token, long expiresInSeconds) {
    }
}
