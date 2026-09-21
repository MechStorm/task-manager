package com.javarush.fedorov.taskmanager.configuration;

import com.javarush.fedorov.taskmanager.support.IntegrationTestBase;
import com.jayway.jsonpath.DocumentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

@DisplayName("OpenAPI spec — security scheme and error responses")
class OpenApiConfigIntegrationTest extends IntegrationTestBase {

    private static final String PROBLEM_DETAIL_REF = "#/components/schemas/ProblemDetail";

    @Test
    @DisplayName("a Bearer JWT scheme is declared and required by default")
    void declaresBearerScheme() {
        DocumentContext spec = spec();

        assertThat(spec.read("$.components.securitySchemes.bearerAuth.type", String.class)).isEqualTo("http");
        assertThat(spec.read("$.components.securitySchemes.bearerAuth.scheme", String.class)).isEqualTo("bearer");
        assertThat(spec.read("$.components.securitySchemes.bearerAuth.bearerFormat", String.class)).isEqualTo("JWT");
        assertThat(spec.read("$.security[0].bearerAuth", List.class)).isEmpty();
    }

    @Test
    @DisplayName("registration and login are documented as public and don't advertise 401 for a missing token")
    void authEndpointsArePublic() {
        DocumentContext spec = spec();

        assertThat(spec.read("$.paths['/api/auth/register'].post.security", List.class)).isEmpty();
        assertThat(spec.read("$.paths['/api/auth/login'].post.security", List.class)).isEmpty();
        assertThat(spec.read("$.paths['/api/auth/register'].post.responses", Map.class)).doesNotContainKey("401");
    }

    @Test
    @DisplayName("every operation that requires a token documents 401")
    void securedOperationsDocumentUnauthorized() {
        List<Map<String, Object>> operations = spec().read("$.paths.*.*");

        List<Map<String, Object>> secured = operations.stream()
                .filter(operation -> !operation.containsKey("security"))
                .toList();

        assertThat(secured).isNotEmpty();
        assertThat(secured).allSatisfy(operation ->
                assertThat(operation.get("responses")).asInstanceOf(MAP).containsKey("401"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"400", "401", "403", "404", "409"})
    @DisplayName("every error response is documented as application/problem+json with the ProblemDetail schema")
    void errorResponsesUseProblemDetail(String code) {
        DocumentContext spec = spec();

        List<Object> responses = spec.read("$.paths.*.*.responses['" + code + "']");
        List<String> refs = spec.read(
                "$.paths.*.*.responses['" + code + "'].content['application/problem+json'].schema['$ref']");

        assertThat(responses).isNotEmpty();
        assertThat(refs).hasSize(responses.size()).containsOnly(PROBLEM_DETAIL_REF);
    }

    @Test
    @DisplayName("the documented error format matches what the API actually returns")
    void documentedErrorFormatMatchesRealResponses() {
        Actor alice = createUser("alice@openapi.test");

        client.get()
                .uri("/api/tasks")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        client.get()
                .uri("/api/tasks/{id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").exists();
    }

    @Test
    @DisplayName("the example body of POST /api/tasks can be sent as is by a regular user")
    void createTaskExampleWorksAsIs() {
        Actor alice = createUser("alice@openapi.test");
        Map<String, Object> example = exampleOf("CreateTaskRequestDto");

        assertThat(example).doesNotContainKey("userId");

        client.post()
                .uri("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(example))
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    @DisplayName("the example body of PUT /api/tasks/{id} can be sent as is by the owner")
    void updateTaskExampleWorksAsIs() {
        Actor alice = createUser("alice@openapi.test");
        UUID taskId = createTask(alice, "Alice's task", alice.id());
        Map<String, Object> example = exampleOf("UpdateTaskRequestDto");

        assertThat(example).doesNotContainKey("userId");

        client.put()
                .uri("/api/tasks/{id}", taskId)
                .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(example))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("the example body of PUT /api/users/{id} doesn't silently change the password")
    void updateUserExampleKeepsPassword() {
        Actor alice = createUser("alice@openapi.test");
        Map<String, Object> example = exampleOf("UpdateUserRequestDto");

        client.put()
                .uri("/api/users/{id}", alice.id())
                .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(example))
                .exchange()
                .expectStatus().isOk();

        login(alice.email(), alice.password());
    }

    private Map<String, Object> exampleOf(String schemaName) {
        return spec().read("$.components.schemas." + schemaName + ".example");
    }

    private DocumentContext spec() {
        return bodyOf(client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk());
    }
}
