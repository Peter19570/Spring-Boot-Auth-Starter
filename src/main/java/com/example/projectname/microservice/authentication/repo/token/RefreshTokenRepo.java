package com.example.projectname.microservice.authentication.repo.token;

import com.example.projectname.microservice.authentication.model.token.RefreshToken;
import com.example.projectname.microservice.authentication.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepo extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId")
    void revokeAllByUserId(UUID userId);

    // for the active sessions screen later
    List<RefreshToken> findAllByUserAndRevokedFalseAndExpiresAtAfter(User user, Instant now);

    void deleteByUser(User user);
}
