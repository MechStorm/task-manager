package com.javarush.fedorov.taskmanager.configuration;

import com.javarush.fedorov.taskmanager.model.entity.Role;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("UserSetting — creating configured users on startup")
class UserSettingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserSetting userSetting;

    @Test
    @DisplayName("the configured administrator is created with the ADMIN role and a working password")
    void createsConfiguredAdmin() throws Exception {
        List<SecurityProperties.ApiUser> configured = securityProperties.getUsers();
        assertThat(configured).as("application.yaml must define at least one startup user").isNotEmpty();

        userSetting.run();

        SecurityProperties.ApiUser expected = configured.getFirst();
        User created = userRepository.findByEmail(expected.getEmail()).orElseThrow();

        assertThat(created.getName()).isEqualTo(expected.getName());
        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches(expected.getPassword(), created.getPassword()))
                .as("the password is stored as a hash but matches the configured one")
                .isTrue();

        String token = login(expected.getEmail(), expected.getPassword());

        client.get()
                .uri("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("a second run is idempotent: no duplicates are created")
    void secondRunDoesNotDuplicateUsers() throws Exception {
        userSetting.run();
        long afterFirstRun = userRepository.count();

        assertThatCode(() -> userSetting.run()).doesNotThrowAnyException();

        assertThat(userRepository.count()).isEqualTo(afterFirstRun);
        assertThat(userRepository.findAll())
                .extracting(User::getEmail)
                .doesNotHaveDuplicates();
    }
}
