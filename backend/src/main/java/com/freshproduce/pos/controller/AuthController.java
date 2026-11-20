package com.freshproduce.pos.controller;

import com.freshproduce.pos.dto.Requests.LoginRequest;
import com.freshproduce.pos.dto.Responses.UserDto;
import com.freshproduce.pos.repository.UserRepository;
import com.freshproduce.pos.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** UC-06 Log In. Logout is handled by Spring Security at POST /api/auth/logout. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          UserRepository userRepository, CurrentUserService currentUserService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    /** Issues the CSRF cookie the front-end needs before its first POST. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("headerName", token == null ? "X-XSRF-TOKEN" : token.getHeaderName());
    }

    @PostMapping("/login")
    public UserDto login(@Valid @RequestBody LoginRequest request,
                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username().trim().toLowerCase(),
                        request.password()));
        // New session id after login to prevent session fixation.
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        return userRepository.findByUsername(authentication.getName()).map(UserDto::from).orElseThrow();
    }

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.from(currentUserService.get());
    }
}
