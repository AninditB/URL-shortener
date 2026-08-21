package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.RegisterRequest;
import com.aninditb.shortlink.dto.UserResponse;
import com.aninditb.shortlink.entity.User;
import com.aninditb.shortlink.exception.EmailAlreadyExistsException;
import com.aninditb.shortlink.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.email());
        }

        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        user = repository.save(user);

        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }
}
