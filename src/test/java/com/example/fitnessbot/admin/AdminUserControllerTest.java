package com.example.fitnessbot.admin;

import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import(AdminSecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @Test
    void updateParserFlagRequiresBasicAuth() throws Exception {
        mockMvc.perform(put("/admin/users/123/parser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "useAiParser": true
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateParserFlagUpdatesExistingUser() throws Exception {
        User user = new User();
        user.setTelegramId(123L);
        user.setUseAiParser(false);

        when(userRepository.findByTelegramId(123L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/admin/users/123/parser")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "useAiParser": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramUserId").value(123))
                .andExpect(jsonPath("$.useAiParser").value(true));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateParserFlagReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        when(userRepository.findByTelegramId(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/admin/users/999/parser")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "useAiParser": false
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
