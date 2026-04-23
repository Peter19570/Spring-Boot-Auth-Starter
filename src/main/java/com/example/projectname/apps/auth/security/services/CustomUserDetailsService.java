package com.example.projectname.apps.auth.security.services;

import com.example.projectname.apps.auth.dto.internal.CustomUserPrincipal;
import com.example.projectname.apps.users.model.User;
import com.example.projectname.apps.users.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

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
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            return new CustomUserPrincipal(user, null);
        };
    }
}
