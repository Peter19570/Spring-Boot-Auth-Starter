package com.example.projectname.microservice.authentication.security.userservice;

import com.example.projectname.microservice.authentication.dto.internal.CustomUserPrincipal;
import com.example.projectname.microservice.authentication.exception.UserNotFoundException;
import com.example.projectname.microservice.authentication.model.User;
import com.example.projectname.microservice.authentication.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;

@Configuration
@RequiredArgsConstructor
public class CustomUserDetailsService {

    private final UserRepo userRepo;

    /**
     * Provide source of data to enable spring security find, verify and authenticate users
     * */
    @Bean
    public UserDetailsService userDetailsService(){
        return username -> {
            User user = userRepo.findByEmailAndDeletedAtIsNull(username)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));

            return new CustomUserPrincipal(user, null);
        };
    }
}
