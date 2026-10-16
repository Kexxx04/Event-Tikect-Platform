package com.eventplatform.api.service;

import com.eventplatform.api.dto.user.CreateUserRequest;
import com.eventplatform.api.dto.user.UpdateUserRequest;
import com.eventplatform.api.dto.user.UserResponse;
import com.eventplatform.api.exception.DuplicateUserException;
import com.eventplatform.api.exception.InvalidUserDataException;
import com.eventplatform.api.exception.UserNotFoundException;
import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.UserStatus;
import com.eventplatform.api.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String name = normalizeRequired(request.name(), "Name");
        String email = normalizeEmail(request.email());
        String document = normalizeRequired(request.document(), "Document");
        validateUniqueFields(email, document, null);

        User user = new User(name, email, document);
        return UserResponse.from(userRepository.save(user));
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse findById(Long id) {
        return UserResponse.from(getUser(id));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = getUser(id);
        String name = request.name() == null
                ? user.getName()
                : normalizeRequired(request.name(), "Name");
        String email = request.email() == null
                ? user.getEmail()
                : normalizeEmail(request.email());
        String document = request.document() == null
                ? user.getDocument()
                : normalizeRequired(request.document(), "Document");

        validateUniqueFields(email, document, id);
        user.setName(name);
        user.setEmail(email);
        user.setDocument(document);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateStatus(Long id, UserStatus status) {
        User user = getUser(id);
        user.setStatus(status);
        return UserResponse.from(user);
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private void validateUniqueFields(String email, String document, Long currentUserId) {
        boolean emailExists = currentUserId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, currentUserId);
        if (emailExists) {
            throw new DuplicateUserException("Email is already registered");
        }

        boolean documentExists = currentUserId == null
                ? userRepository.existsByDocument(document)
                : userRepository.existsByDocumentAndIdNot(document, currentUserId);
        if (documentExists) {
            throw new DuplicateUserException("Document is already registered");
        }
    }

    private String normalizeEmail(String email) {
        return normalizeRequired(email, "Email").toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new InvalidUserDataException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
