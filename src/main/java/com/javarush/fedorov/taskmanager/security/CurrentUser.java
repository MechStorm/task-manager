package com.javarush.fedorov.taskmanager.security;

import com.javarush.fedorov.taskmanager.model.entity.Role;

import java.util.UUID;

public record CurrentUser(UUID id, Role role) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
