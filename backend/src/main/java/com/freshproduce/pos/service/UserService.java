package com.freshproduce.pos.service;

import com.freshproduce.pos.domain.User;
import com.freshproduce.pos.dto.Requests.CreateUserRequest;
import com.freshproduce.pos.dto.Requests.UpdateUserRequest;
import com.freshproduce.pos.dto.Responses.UserDto;
import com.freshproduce.pos.exception.BusinessException;
import com.freshproduce.pos.exception.NotFoundException;
import com.freshproduce.pos.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** User management for Administrators. Passwords are only ever stored as BCrypt hashes. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return userRepository.findAll().stream().map(UserDto::from).toList();
    }

    @Transactional
    public UserDto create(CreateUserRequest request) {
        String username = request.username().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("Username '" + username + "' is already taken");
        }
        User user = new User(username, passwordEncoder.encode(request.password()), request.fullName().trim(),
                request.role());
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest request, User currentAdmin) {
        User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getId().equals(currentAdmin.getId())
                && (!"ACTIVE".equals(request.status()) || request.role() != currentAdmin.getRole())) {
            throw new BusinessException("You cannot deactivate your own account or change your own role");
        }
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setStatus(request.status());
        if (StringUtils.hasText(request.newPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }
        return UserDto.from(user);
    }
}
