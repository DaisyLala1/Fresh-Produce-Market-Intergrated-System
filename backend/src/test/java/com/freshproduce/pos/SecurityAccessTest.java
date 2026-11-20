package com.freshproduce.pos;

import com.freshproduce.pos.domain.Role;
import com.freshproduce.pos.domain.User;
import com.freshproduce.pos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TC-06 / ST-01: authentication and role-based access control, including direct API access. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void unauthenticatedApiAccessIsRejected() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentCannotOpenUserManagement() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentCannotCreateProductsDirectlyThroughApi() throws Exception {
        mvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"category\":\"Fruit\",\"unitOfMeasure\":\"kg\",\"sellingPrice\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void agentCannotChangeCommissionRate() throws Exception {
        mvc.perform(put("/api/commission-rates").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratePercent\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorCanOpenUserManagement() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isOk());
    }

    @Test
    void validCredentialsLogIn() throws Exception {
        userRepository.save(new User("logintest", passwordEncoder.encode("Secret@123"), "Login Test", Role.AGENT));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"logintest\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        userRepository.save(new User("logintest2", passwordEncoder.encode("Secret@123"), "Login Test", Role.AGENT));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"logintest2\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isForbidden());
    }
}
