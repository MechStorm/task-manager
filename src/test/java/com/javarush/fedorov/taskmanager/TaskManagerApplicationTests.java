package com.javarush.fedorov.taskmanager;

import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The application starts up as a whole")
class TaskManagerApplicationTests extends IntegrationTestBase {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("the context is built, key beans are in place, migrations applied")
    void contextLoads() {
        assertThat(applicationContext.getBeanNamesForType(org.springframework.security.web.SecurityFilterChain.class))
                .isNotEmpty();
        assertThat(applicationContext.containsBean("jwtAuthenticationFilter")).isTrue();
        assertThat(userRepository.count()).isNotNegative();
        assertThat(taskRepository.count()).isNotNegative();
    }

    @Test
    @DisplayName("the application answers over HTTP on its assigned port")
    void servesHttp() {
        assertThat(port).isPositive();

        client.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
