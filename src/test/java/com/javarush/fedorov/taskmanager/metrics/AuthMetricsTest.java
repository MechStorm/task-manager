package com.javarush.fedorov.taskmanager.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthMetrics — login counters")
class AuthMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final AuthMetrics metrics = new AuthMetrics(registry);
    private final Authentication authentication =
            UsernamePasswordAuthenticationToken.unauthenticated("user@example.com", "password");

    @Test
    @DisplayName("success and failure counters exist at zero before the first login")
    void registersCountersUpFront() {
        assertThat(logins("success")).isZero();
        assertThat(logins("failure")).isZero();
    }

    @Test
    @DisplayName("a success event increments only result=success")
    void countsSuccess() {
        metrics.onSuccess(new AuthenticationSuccessEvent(authentication));

        assertThat(logins("success")).isEqualTo(1.0);
        assertThat(logins("failure")).isZero();
    }

    @Test
    @DisplayName("every kind of failure event increments result=failure")
    void countsEveryFailureKind() {
        metrics.onFailure(new AuthenticationFailureBadCredentialsEvent(
                authentication, new BadCredentialsException("Bad credentials")));
        metrics.onFailure(new AuthenticationFailureDisabledEvent(
                authentication, new DisabledException("User is disabled")));

        assertThat(logins("failure")).isEqualTo(2.0);
        assertThat(logins("success")).isZero();
    }

    private double logins(String result) {
        return registry.get("auth.login").tag("result", result).counter().count();
    }
}
