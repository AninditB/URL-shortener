package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.LoginRequest;
import com.aninditb.shortlink.dto.RegisterRequest;
import com.aninditb.shortlink.dto.TokenResponse;
import com.aninditb.shortlink.dto.UserResponse;
import com.aninditb.shortlink.entity.User;
import com.aninditb.shortlink.entity.UserRole;
import com.aninditb.shortlink.exception.EmailAlreadyExistsException;
import com.aninditb.shortlink.exception.InvalidCredentialsException;
import com.aninditb.shortlink.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository repository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserServiceImpl service;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Test
    void registerHashesPasswordAndReturnsUserResponse() {
        RegisterRequest request = new RegisterRequest("new@example.com", "plaintext-pw");
        when(repository.existsByEmail("new@example.com")).thenReturn(false);
        when(repository.save(userCaptor.capture())).thenAnswer(invocation -> {
            User entity = invocation.getArgument(0);
            setRole(entity, UserRole.USER); // mimics the @PrePersist default a real save() would apply
            return entity;
        });

        UserResponse response = service.register(request);

        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.role()).isEqualTo("USER");

        User saved = userCaptor.getValue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("plaintext-pw");
        assertThat(saved.getPasswordHash()).startsWith("$2");
    }

    @Test
    void registerWithTakenEmailThrowsConflict() {
        RegisterRequest request = new RegisterRequest("taken@example.com", "plaintext-pw");
        when(repository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void loginWithCorrectCredentialsReturnsToken() {
        String hash = new BCryptPasswordEncoder().encode("correct-password");
        User user = new User("existing@example.com", hash);
        setId(user, 5L);
        setRole(user, UserRole.ADMIN);
        when(repository.findByEmail("existing@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(5L, "ADMIN")).thenReturn("signed.jwt.token");

        TokenResponse response = service.login(new LoginRequest("existing@example.com", "correct-password"));

        assertThat(response.token()).isEqualTo("signed.jwt.token");
    }

    @Test
    void loginWithWrongPasswordThrowsInvalidCredentials() {
        String hash = new BCryptPasswordEncoder().encode("correct-password");
        User user = new User("existing@example.com", hash);
        setId(user, 5L);
        when(repository.findByEmail("existing@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("existing@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithUnknownEmailThrowsInvalidCredentials() {
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("missing@example.com", "any-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private static void setRole(User entity, UserRole role) {
        try {
            Field field = User.class.getDeclaredField("role");
            field.setAccessible(true);
            field.set(entity, role);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setId(User entity, long id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
