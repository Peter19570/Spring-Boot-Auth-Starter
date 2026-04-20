package com.example.projectname.microservice.authentication.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "email_verification_tokens")
public class EmailVerificationToken{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column
    private String newEmail;

    @Column(nullable = false)
    private boolean used = false;

    @Column(nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    private Instant createdAt;
}
