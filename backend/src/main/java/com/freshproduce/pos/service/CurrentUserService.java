package com.freshproduce.pos.service;

import com.freshproduce.pos.domain.Role;
import com.freshproduce.pos.domain.User;
import com.freshproduce.pos.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Resolves the logged-in Agent or Administrator. */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Not logged in");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Not logged in"));
    }

    public boolean isAdministrator() {
        return get().getRole() == Role.ADMINISTRATOR;
    }
}
