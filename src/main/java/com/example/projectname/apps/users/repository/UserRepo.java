package com.example.projectname.apps.user.repository;

import com.example.projectname.apps.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepo extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);

    // The "JOIN FETCH" tells Hibernate to grab the list in the same SQL query
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.socialAccounts WHERE u.email = :email")
    Optional<User> findByEmailWithSocialAccounts(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.socialAccounts WHERE u.id = :id")
    Optional<User> findByIdWithSocialAccounts(@Param("id") UUID id);

}
