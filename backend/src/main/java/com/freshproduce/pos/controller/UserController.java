package com.freshproduce.pos.controller;

import com.freshproduce.pos.dto.Requests.CreateUserRequest;
import com.freshproduce.pos.dto.Requests.UpdateUserRequest;
import com.freshproduce.pos.dto.Responses.UserDto;
import com.freshproduce.pos.service.CurrentUserService;
import com.freshproduce.pos.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Manage Users – Administrator only (URL rule in SecurityConfig plus method-level check). */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request, currentUserService.get());
    }
}
