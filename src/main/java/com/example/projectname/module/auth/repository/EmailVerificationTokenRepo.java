package com.example.projectname.module.auth.repository.token;

import com.example.projectname.module.auth.model.token.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepo extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

}