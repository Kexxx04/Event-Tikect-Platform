package com.eventplatform.api.dto.user;

import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.UserStatus;

public record UserResponse(
        Long id,
        String name,
        String email,
        String document,
        UserStatus status
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getDocument(),
                user.getStatus()
        );
    }
}
