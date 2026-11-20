package com.eventplatform.api.controller;

import com.eventplatform.api.dto.auth.CurrentUserResponse;
import com.eventplatform.api.dto.auth.LoginRequest;
import com.eventplatform.api.dto.auth.LoginResponse;
import com.eventplatform.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return new CurrentUserResponse(authentication.getName());
    }
}
