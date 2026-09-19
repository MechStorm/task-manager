package com.javarush.fedorov.taskmanager.model.entity;

import com.javarush.fedorov.taskmanager.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserSecureWrapper implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final Role role;

    public UserSecureWrapper(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.role = user.getRole();
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public CurrentUser toCurrentUser() {
        return new CurrentUser(id, role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
