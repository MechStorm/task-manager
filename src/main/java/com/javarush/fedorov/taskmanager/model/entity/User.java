package com.javarush.fedorov.taskmanager.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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

    @Builder.Default
    @OneToMany(mappedBy = "owner")
    private List<Task> tasks = new ArrayList<>();
}
