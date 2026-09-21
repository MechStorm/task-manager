package com.javarush.fedorov.taskmanager.support;

import com.javarush.fedorov.taskmanager.configuration.SecurityProperties;
import com.javarush.fedorov.taskmanager.model.entity.Role;
import com.javarush.fedorov.taskmanager.model.entity.User;
import com.javarush.fedorov.taskmanager.model.repository.TaskRepository;
import com.javarush.fedorov.taskmanager.model.repository.UserRepository;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@ActiveProfiles("test")
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    protected static final String DEFAULT_PASSWORD = "password123";

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected SecurityProperties securityProperties;

    protected RestTestClient client;

    @BeforeEach
    void prepareClientAndDatabase() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        taskRepository.deleteAll();
        userRepository.deleteAll();
    }

    public record Actor(UUID id, String name, String email, String password, Role role, String token) {

        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected Actor createActor(String name, String email, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRole(role);

        User saved = userRepository.saveAndFlush(user);

        return new Actor(saved.getId(), name, email, DEFAULT_PASSWORD, role,
                login(email, DEFAULT_PASSWORD));
    }

    protected Actor createUser(String email) {
        return createActor("User " + email, email, Role.USER);
    }

    protected Actor createAdmin(String email) {
        return createActor("Admin " + email, email, Role.ADMIN);
    }

    protected String login(String email, String password) {
        RestTestClient.ResponseSpec response = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("email", email, "password", password))
                .exchange()
                .expectStatus().isOk();

        return bodyOf(response).read("$.token", String.class);
    }

    protected UUID createTask(Actor author, String title, UUID assigneeId) {
        RestTestClient.ResponseSpec response = client.post()
                .uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, author.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", title, "userId", assigneeId, "deadline", futureDeadline()))
                .exchange()
                .expectStatus().isCreated();

        return UUID.fromString(bodyOf(response).read("$.id", String.class));
    }

    protected UUID createUnassignedTask(Actor author, String title) {
        return createTask(author, title, null);
    }

    protected void startTask(Actor owner, UUID taskId) {
        client.put()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("status", "IN_PROGRESS"))
                .exchange()
                .expectStatus().isOk();
    }

    protected String json(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Expected key-value pairs");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            payload.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }

        return objectMapper.writeValueAsString(payload);
    }

    protected DocumentContext bodyOf(RestTestClient.ResponseSpec response) {
        byte[] body = response.expectBody().returnResult().getResponseBody();
        return JsonPath.parse(new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8));
    }

    protected Instant futureDeadline() {
        return Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    }

    protected Instant pastDeadline() {
        return Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    }
}
