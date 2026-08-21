package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.RegisterRequest;
import com.aninditb.shortlink.dto.UserResponse;

public interface UserService {

    UserResponse register(RegisterRequest request);
}
