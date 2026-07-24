package com.documentai.platform.service;

import com.documentai.platform.dto.request.LoginRequest;
import com.documentai.platform.dto.request.RegisterRequest;
import com.documentai.platform.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
