package com.eventplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventplatform.api.dto.user.CreateUserRequest;
import com.eventplatform.api.dto.user.UserResponse;
import com.eventplatform.api.exception.DuplicateUserException;
import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.UserStatus;
import com.eventplatform.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createsAnActiveUserWithNormalizedData() {
        CreateUserRequest request = new CreateUserRequest(
                "  Ada Lovelace  ",
                "  ADA@EXAMPLE.COM  ",
                "  12345  "
        );
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(userRepository.existsByDocument("12345")).thenReturn(false);
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(1L);
                    return user;
                });

        UserResponse response = userService.create(request);

        assertEquals(1L, response.id());
        assertEquals("Ada Lovelace", response.name());
        assertEquals("ada@example.com", response.email());
        assertEquals("12345", response.document());
        assertEquals(UserStatus.ACTIVE, response.status());
    }

    @Test
    void rejectsAnEmailThatIsAlreadyRegistered() {
        CreateUserRequest request = new CreateUserRequest(
                "Grace Hopper",
                "grace@example.com",
                "67890"
        );
        when(userRepository.existsByEmailIgnoreCase("grace@example.com")).thenReturn(true);

        DuplicateUserException exception = assertThrows(
                DuplicateUserException.class,
                () -> userService.create(request)
        );

        assertEquals("Email is already registered", exception.getMessage());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }
}
