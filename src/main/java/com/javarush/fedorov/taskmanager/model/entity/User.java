package com.javarush.fedorov.taskmanager.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "name",  nullable = false)
    private String name;

    @Column(name = "email",  nullable = false, unique = true)
    private String email;

    @Column(name = "password",  nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Builder.Default
    @OneToMany(mappedBy = "owner")
    private List<Task> tasks = new ArrayList<>();
}
