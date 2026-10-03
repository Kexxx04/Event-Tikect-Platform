package com.eventplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventplatform.api.dto.user.CreateUserRequest;
import com.eventplatform.api.dto.user.UserResponse;
import com.eventplatform.api.dto.user.UpdateUserRequest;
import com.eventplatform.api.exception.DuplicateUserException;
import com.eventplatform.api.exception.InvalidUserDataException;
import com.eventplatform.api.exception.UserNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

    private User existingUser() {
        User user = new User("Ada", "ada@example.com", "12345");
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void retrievesAUserById() {
        User user = existingUser();
        assertEquals(UserResponse.from(user), userService.findById(1L));
    }

    @Test
    void listsUsersInIdOrder() {
        User user = new User("Ada", "ada@example.com", "12345");
        user.setId(1L);
        when(userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of(user));
        assertEquals(List.of(UserResponse.from(user)), userService.findAll());
    }

    @Test
    void rejectsMissingUsersForReadUpdateAndStatus() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> userService.findById(99L));
        assertThrows(UserNotFoundException.class,
                () -> userService.update(99L, new UpdateUserRequest("Ada", null, null)));
        assertThrows(UserNotFoundException.class, () -> userService.updateStatus(99L, UserStatus.INACTIVE));
    }

    @Test
    void updatesAndNormalizesAllFields() {
        User user = existingUser();
        UserResponse response = userService.update(1L,
                new UpdateUserRequest("  Grace  ", "  GRACE@EXAMPLE.COM  ", "  67890  "));
        assertEquals("Grace", response.name());
        assertEquals("grace@example.com", user.getEmail());
        assertEquals("67890", user.getDocument());
        verify(userRepository).existsByEmailIgnoreCaseAndIdNot("grace@example.com", 1L);
        verify(userRepository).existsByDocumentAndIdNot("67890", 1L);
    }

    @Test
    void preservesOmittedFields() {
        User user = existingUser();
        UserResponse before = UserResponse.from(user);
        assertEquals(before, userService.update(1L, new UpdateUserRequest(null, null, null)));
    }

    @Test
    void rejectsDuplicateEmailOnUpdateWithoutChangingUser() {
        User user = existingUser();
        UserResponse before = UserResponse.from(user);
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("other@example.com", 1L)).thenReturn(true);
        assertThrows(DuplicateUserException.class,
                () -> userService.update(1L, new UpdateUserRequest("Other", "other@example.com", null)));
        assertEquals(before, UserResponse.from(user));
    }

    @Test
    void rejectsDuplicateDocumentOnUpdateWithoutChangingUser() {
        User user = existingUser();
        UserResponse before = UserResponse.from(user);
        when(userRepository.existsByDocumentAndIdNot("67890", 1L)).thenReturn(true);
        assertThrows(DuplicateUserException.class,
                () -> userService.update(1L, new UpdateUserRequest("Other", null, "67890")));
        assertEquals(before, UserResponse.from(user));
    }

    @Test
    void changesStatusAndPreservesPersonalData() {
        User user = existingUser();
        assertEquals(UserStatus.INACTIVE, userService.updateStatus(1L, UserStatus.INACTIVE).status());
        assertEquals(UserStatus.INACTIVE, user.getStatus());
        assertEquals(UserStatus.ACTIVE, userService.updateStatus(1L, UserStatus.ACTIVE).status());
        assertEquals("ada@example.com", user.getEmail());
        assertEquals("12345", user.getDocument());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingRequiredFields(String invalid) {
        assertThrows(InvalidUserDataException.class,
                () -> userService.create(new CreateUserRequest(invalid, "ada@example.com", "12345")));
        assertThrows(InvalidUserDataException.class,
                () -> userService.create(new CreateUserRequest("Ada", invalid, "12345")));
        assertThrows(InvalidUserDataException.class,
                () -> userService.create(new CreateUserRequest("Ada", "ada@example.com", invalid)));
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void rejectsDuplicateDocumentOnCreate() {
        when(userRepository.existsByDocument("12345")).thenReturn(true);
        assertThrows(DuplicateUserException.class,
                () -> userService.create(new CreateUserRequest("Ada", "ada@example.com", "12345")));
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

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
