package com.javarush.fedorov.taskmanager.configuration;

import com.javarush.fedorov.taskmanager.model.entity.Role;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();

    private List<ApiUser> users = new ArrayList<>();

    @Getter
    @Setter
    public static class Jwt {
        private String issuer;
        private String secret;
        private long accessTokenValidityMinutes = 30;
    }

    @Getter
    @Setter
    public static class ApiUser {
        private String name;
        private String email;
        private String password;
        private Role role = Role.USER;
    }
}
