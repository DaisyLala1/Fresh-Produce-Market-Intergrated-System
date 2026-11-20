package com.freshproduce.pos.config;

import com.freshproduce.pos.security.CsrfCookieFilter;
import com.freshproduce.pos.security.SpaCsrfTokenRequestHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

/**
 * Role-based access control (FR-06, NFR-02). Rules are enforced on the URL/API itself, so an Agent
 * cannot reach Administrator-only functions even by typing the URL or calling the API directly.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository) throws Exception {
        http
            .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                    // Static front-end files
                    .requestMatchers("/", "/index.html", "/app.html", "/css/**", "/js/**", "/img/**", "/vendor/**",
                            "/favicon.ico", "/error").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                    // Administrator-only functions
                    .requestMatchers("/api/users/**").hasRole("ADMINISTRATOR")
                    .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMINISTRATOR")
                    .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMINISTRATOR")
                    .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasRole("ADMINISTRATOR")
                    .requestMatchers(HttpMethod.PUT, "/api/commission-rates/**").hasRole("ADMINISTRATOR")
                    .requestMatchers(HttpMethod.PATCH, "/api/farmers/*/status").hasRole("ADMINISTRATOR")
                    // Everything else in the API requires a logged-in Agent or Administrator
                    .requestMatchers("/api/**").hasAnyRole("AGENT", "ADMINISTRATOR")
                    .anyRequest().permitAll())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler((request, response, denied) -> {
                        String message = denied instanceof CsrfException
                                ? "Your session token has expired. Please refresh the page and try again."
                                : "You do not have permission to access this function";
                        response.setStatus(HttpStatus.FORBIDDEN.value());
                        response.setContentType("application/json");
                        response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\""
                                + message + "\"}");
                    }))
            .logout(logout -> logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((request, response, auth) -> response.setStatus(HttpStatus.OK.value()))
                    .deleteCookies("JSESSIONID"))
            .sessionManagement(session -> session.sessionFixation(fix -> fix.changeSessionId()));
        return http.build();
    }
}
