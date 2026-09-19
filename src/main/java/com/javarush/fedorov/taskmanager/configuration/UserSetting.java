package com.javarush.fedorov.taskmanager.configuration;

import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserSetting implements CommandLineRunner {

    private final SecurityProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        for(SecurityProperties.ApiUser user : properties.getUsers()) {
            if (userRepository.findByEmail(user.getEmail()).isPresent()) {
                log.info("User with email {} already exists", user.getEmail());
                continue;
            }

            User newUser = new User();
            newUser.setName(user.getName());
            newUser.setEmail(user.getEmail());
            newUser.setPassword(passwordEncoder.encode(user.getPassword()));
            newUser.setRole(user.getRole());

            User savedUser = userRepository.save(newUser);

            log.info("User with email {} has been saved", user.getEmail());
        }
    }
}
