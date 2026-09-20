package com.javarush.fedorov.taskmanager.service;

import com.javarush.fedorov.taskmanager.model.entity.Role;
import com.javarush.fedorov.taskmanager.model.entity.UserSecureWrapper;
import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("CustomUserDetailsService — loading a user for authentication")
class CustomUserDetailsServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("a user is loaded by email together with the role")
    void loadsUserByEmail() {
        Actor admin = createAdmin("admin@details.test");

        UserDetails details = userDetailsService.loadUserByUsername(admin.email());

        assertThat(details).isInstanceOf(UserSecureWrapper.class);
        assertThat(details.getUsername()).isEqualTo(admin.email());
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(((UserSecureWrapper) details).getId()).isEqualTo(admin.id());
        assertThat(((UserSecureWrapper) details).getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, details.getPassword())).isTrue();
    }

    @Test
    @DisplayName("an unknown email leads to UsernameNotFoundException")
    void failsForUnknownEmail() {
        assertThatExceptionOfType(UsernameNotFoundException.class)
                .isThrownBy(() -> userDetailsService.loadUserByUsername("ghost@details.test"))
                .withMessage("User not found: ghost@details.test");
    }

    @Test
    @DisplayName("the email lookup is case-sensitive, just like the unique index in the database")
    void lookupIsCaseSensitive() {
        createUser("mixedcase@details.test");

        assertThatExceptionOfType(UsernameNotFoundException.class)
                .isThrownBy(() -> userDetailsService.loadUserByUsername("MixedCase@details.test"));
    }
}
