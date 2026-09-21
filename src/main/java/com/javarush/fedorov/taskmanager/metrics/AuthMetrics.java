package com.javarush.fedorov.taskmanager.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final Counter success;
    private final Counter failure;

    public AuthMetrics(MeterRegistry registry) {
        this.success = Counter.builder("auth.login").tag("result", "success").description("Login attempts").register(registry);
        this.failure = Counter.builder("auth.login").tag("result", "failure").description("Login attempts").register(registry);
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        success.increment();
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        failure.increment();
    }
}
