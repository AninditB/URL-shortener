package com.aninditb.shortlink.controller;

import com.aninditb.shortlink.dto.UserResponse;
import com.aninditb.shortlink.exception.EmailAlreadyExistsException;
import com.aninditb.shortlink.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

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
}
