package com.example.projectname.microservice.authentication.repo;

import com.example.projectname.microservice.authentication.model.PasswordResetToken;
import com.example.projectname.microservice.authentication.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepo extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // clean up old tokens for a baseUser before issuing a new one
    void deleteAllByUser(User user);
}
