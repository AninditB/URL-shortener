package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.LoginRequest;
import com.aninditb.shortlink.dto.RegisterRequest;
import com.aninditb.shortlink.dto.TokenResponse;
import com.aninditb.shortlink.dto.UserResponse;
import com.aninditb.shortlink.entity.User;
import com.aninditb.shortlink.exception.EmailAlreadyExistsException;
import com.aninditb.shortlink.exception.InvalidCredentialsException;
import com.aninditb.shortlink.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final UserRepository repository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(UserRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
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

    @Override
    public TokenResponse login(LoginRequest request) {
        User user = repository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        String token = jwtService.generateToken(user.getId(), user.getRole().name());
        return new TokenResponse(token);
    }
}
