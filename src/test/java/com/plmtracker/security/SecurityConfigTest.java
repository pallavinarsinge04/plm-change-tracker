package com.plmtracker.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void blankPassword_meansNoSuchAccount() {
        UserDetailsService users = config.userDetailsService(
                config.passwordEncoder(), "engineer-password-123", "");

        assertThat(users.loadUserByUsername("engineer").getUsername()).isEqualTo("engineer");
        assertThatThrownBy(() -> users.loadUserByUsername("viewer"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shortPassword_stopsStartup() {
        assertThatThrownBy(() -> config.userDetailsService(
                config.passwordEncoder(), "short", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12");
    }

    @Test
    void roles_areAssignedPerAccount() {
        UserDetailsService users = config.userDetailsService(
                config.passwordEncoder(), "engineer-password-123", "viewer-password-1234");

        assertThat(users.loadUserByUsername("engineer").getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_ENGINEER");
        assertThat(users.loadUserByUsername("viewer").getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_VIEWER");
    }
}
