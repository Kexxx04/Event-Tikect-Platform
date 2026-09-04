package com.eventplatform.api.repository;

import com.eventplatform.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByDocument(String document);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByDocumentAndIdNot(String document, Long id);
}
