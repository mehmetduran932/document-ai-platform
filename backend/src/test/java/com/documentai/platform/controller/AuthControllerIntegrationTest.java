package com.documentai.platform.controller;

import com.documentai.platform.AbstractIntegrationTest;
import com.documentai.platform.dto.request.LoginRequest;
import com.documentai.platform.dto.request.RegisterRequest;
import com.documentai.platform.dto.response.AuthResponse;
import com.documentai.platform.exception.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void registerThenLoginReturnsWorkingToken() {
        RegisterRequest register = new RegisterRequest(
                "alice@example.com", "supersecret1", "Alice Example", "Alice's Workspace");

        ResponseEntity<AuthResponse> registerResponse =
                restTemplate.postForEntity("/api/auth/register", register, AuthResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().accessToken()).isNotBlank();
        assertThat(registerResponse.getBody().workspaceId()).isNotNull();

        LoginRequest login = new LoginRequest("alice@example.com", "supersecret1");
        ResponseEntity<AuthResponse> loginResponse =
                restTemplate.postForEntity("/api/auth/login", login, AuthResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        assertThat(loginResponse.getBody().workspaceId()).isEqualTo(registerResponse.getBody().workspaceId());
    }

    @Test
    void duplicateEmailIsRejectedWithConflict() {
        RegisterRequest register = new RegisterRequest(
                "bob@example.com", "supersecret1", "Bob Example", "Bob's Workspace");
        restTemplate.postForEntity("/api/auth/register", register, AuthResponse.class);

        ResponseEntity<ApiError> secondAttempt =
                restTemplate.postForEntity("/api/auth/register", register, ApiError.class);

        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        RegisterRequest register = new RegisterRequest(
                "carol@example.com", "supersecret1", "Carol Example", "Carol's Workspace");
        restTemplate.postForEntity("/api/auth/register", register, AuthResponse.class);

        LoginRequest badLogin = new LoginRequest("carol@example.com", "wrong-password");
        ResponseEntity<ApiError> response = restTemplate.postForEntity("/api/auth/login", badLogin, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointRejectsMissingToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/documents", String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
