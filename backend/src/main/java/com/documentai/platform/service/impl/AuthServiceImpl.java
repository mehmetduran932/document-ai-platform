package com.documentai.platform.service.impl;

import com.documentai.platform.domain.entity.User;
import com.documentai.platform.domain.entity.Workspace;
import com.documentai.platform.domain.enums.WorkspaceRole;
import com.documentai.platform.dto.request.LoginRequest;
import com.documentai.platform.dto.request.RegisterRequest;
import com.documentai.platform.dto.response.AuthResponse;
import com.documentai.platform.exception.DuplicateResourceException;
import com.documentai.platform.infrastructure.security.JwtService;
import com.documentai.platform.infrastructure.security.UserPrincipal;
import com.documentai.platform.repository.UserRepository;
import com.documentai.platform.repository.WorkspaceRepository;
import com.documentai.platform.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Workspace workspace = Workspace.builder()
                .name(request.workspaceName())
                .build();
        workspace = workspaceRepository.save(workspace);

        User user = User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .workspace(workspace)
                .role(WorkspaceRole.OWNER)
                .build();
        user = userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateAccessToken(principal);
        return AuthResponse.bearer(token, jwtService.accessTokenTtlSeconds(), user.getId(), workspace.getId(), user.getEmail());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));

        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid credentials"));

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateAccessToken(principal);
        return AuthResponse.bearer(token, jwtService.accessTokenTtlSeconds(), user.getId(), user.getWorkspace().getId(), user.getEmail());
    }
}
