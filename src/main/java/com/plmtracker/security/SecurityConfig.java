package com.plmtracker.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

/**
 * Who may call what.
 * <pre>
 *   GET  /api/change-requests/**     VIEWER or ENGINEER
 *   POST/PUT /api/change-requests/** ENGINEER only
 *   POST /api/webhooks/jira          open at the filter level, protected by its own secret token
 *   GET  /actuator/health            open (for uptime checks)
 *   everything else                  denied
 * </pre>
 * Stateless HTTP Basic: every request carries its credentials, no session, no cookies,
 * so CSRF protection is not needed. Use HTTPS in production so Basic credentials are encrypted.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/jira").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/change-requests/**").hasAnyRole("VIEWER", "ENGINEER")
                        .requestMatchers("/api/change-requests/**").hasRole("ENGINEER")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // bcrypt by default; passwords are never stored or compared in plain text
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Two accounts, passwords come from environment variables.
     * A blank password means that account does not exist. A too-short password stops the app
     * at startup, so a weak password can never go live by accident.
     */
    @Bean
    UserDetailsService userDetailsService(
            PasswordEncoder encoder,
            @Value("${app.security.engineer-password:}") String engineerPassword,
            @Value("${app.security.viewer-password:}") String viewerPassword) {

        List<UserDetails> users = new ArrayList<>();
        addUser(users, encoder, "engineer", engineerPassword, "ENGINEER");
        addUser(users, encoder, "viewer", viewerPassword, "VIEWER");

        if (users.isEmpty()) {
            log.warn("No login accounts configured. Set TRACKER_ENGINEER_PASSWORD and/or "
                    + "TRACKER_VIEWER_PASSWORD. Until then every API call except health returns 401.");
        }
        return new InMemoryUserDetailsManager(users);
    }

    private static void addUser(List<UserDetails> users, PasswordEncoder encoder,
                                String username, String rawPassword, String role) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return;
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("Password for '" + username + "' must be at least "
                    + MIN_PASSWORD_LENGTH + " characters");
        }
        users.add(User.withUsername(username)
                .password(encoder.encode(rawPassword))
                .roles(role)
                .build());
    }
}
