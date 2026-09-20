package com.javarush.fedorov.taskmanager.model.entity;

import com.javarush.fedorov.taskmanager.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserSecureWrapper — user representation for Spring Security")
class UserSecureWrapperTest {

    @Test
    @DisplayName("the email serves as the login, the password comes from the entity")
    void exposesEmailAsUsername() {
        User user = user(Role.USER);

        UserSecureWrapper wrapper = new UserSecureWrapper(user);

        assertThat(wrapper.getUsername()).isEqualTo(user.getEmail());
        assertThat(wrapper.getPassword()).isEqualTo(user.getPassword());
        assertThat(wrapper.getId()).isEqualTo(user.getId());
        assertThat(wrapper.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("the role turns into a ROLE_-prefixed authority")
    void mapsRoleToAuthority() {
        assertThat(new UserSecureWrapper(user(Role.USER)).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");

        assertThat(new UserSecureWrapper(user(Role.ADMIN)).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("the account is enabled and not locked")
    void accountIsUsable() {
        UserSecureWrapper wrapper = new UserSecureWrapper(user(Role.USER));

        assertThat(wrapper.isEnabled()).isTrue();
        assertThat(wrapper.isAccountNonExpired()).isTrue();
        assertThat(wrapper.isAccountNonLocked()).isTrue();
        assertThat(wrapper.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("toCurrentUser returns id and role, and isAdmin holds only for ADMIN")
    void convertsToCurrentUser() {
        User admin = user(Role.ADMIN);

        CurrentUser currentAdmin = new UserSecureWrapper(admin).toCurrentUser();
        assertThat(currentAdmin.id()).isEqualTo(admin.getId());
        assertThat(currentAdmin.role()).isEqualTo(Role.ADMIN);
        assertThat(currentAdmin.isAdmin()).isTrue();

        assertThat(new UserSecureWrapper(user(Role.USER)).toCurrentUser().isAdmin()).isFalse();
    }

    private static User user(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("User");
        user.setEmail("user@example.com");
        user.setPassword("$2a$10$hash");
        user.setRole(role);
        return user;
    }
}
