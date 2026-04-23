package com.example.projectname.apps.auth.repository;

import com.example.projectname.apps.auth.model.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialAccountRepo extends JpaRepository<SocialAccount, UUID> {

    Optional<SocialAccount> findByProviderAndProviderId(String provider, String providerId);

    @Query("SELECT sa FROM SocialAccount sa JOIN FETCH" +
            " sa.user WHERE sa.provider = :provider " +
            "AND sa.providerId = :providerId")
    Optional<SocialAccount> findByProviderAndProviderIdWithUser(
            @Param("provider") String provider, @Param("providerId") String providerId);
}
