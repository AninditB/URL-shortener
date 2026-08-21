package com.aninditb.shortlink.controller;

import com.aninditb.shortlink.dto.TokenResponse;
import com.aninditb.shortlink.dto.UserResponse;
import com.aninditb.shortlink.exception.EmailAlreadyExistsException;
import com.aninditb.shortlink.exception.InvalidCredentialsException;
import com.aninditb.shortlink.service.JwtService;
import com.aninditb.shortlink.service.UserService;
import com.aninditb.shortlink.web.RateLimitInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    // JwtAuthenticationFilter/SecurityConfig are part of the web-layer slice; JwtService is
    // their only unsatisfied dependency, so it must be mocked even though this test never uses it.
    @MockBean
    private JwtService jwtService;

    // WebMvcConfig (a WebMvcConfigurer, part of the slice) requires a RateLimitInterceptor bean;
    // not stubbed since these /api/v1/auth requests never match its /api/v1/urls path pattern.
    @MockBean
    private RateLimitInterceptor rateLimitInterceptor;

    @Test
    void registerReturns201WithBody() throws Exception {
        when(userService.register(any())).thenReturn(new UserResponse(1L, "new@example.com", "USER"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"new@example.com\",\"password\":\"plaintext-pw\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void registerWithInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"not-an-email\",\"password\":\"plaintext-pw\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithShortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"new@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithTakenEmailReturns409() throws Exception {
        when(userService.register(any())).thenThrow(new EmailAlreadyExistsException("Email already registered: taken@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"taken@example.com\",\"password\":\"plaintext-pw\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void loginWithCorrectCredentialsReturns200WithToken() throws Exception {
        when(userService.login(any())).thenReturn(new TokenResponse("a.b.c"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"new@example.com\",\"password\":\"plaintext-pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("a.b.c"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        when(userService.login(any())).thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"new@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
