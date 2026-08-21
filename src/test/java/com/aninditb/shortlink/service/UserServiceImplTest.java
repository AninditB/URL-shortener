package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.RegisterRequest;
import com.aninditb.shortlink.dto.UserResponse;
import com.aninditb.shortlink.entity.User;
import com.aninditb.shortlink.entity.UserRole;
import com.aninditb.shortlink.exception.EmailAlreadyExistsException;
import com.aninditb.shortlink.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository repository;

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

    private static void setRole(User entity, UserRole role) {
        try {
            Field field = User.class.getDeclaredField("role");
            field.setAccessible(true);
            field.set(entity, role);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
