package com.example.fitnessbot.service;

import com.example.fitnessbot.model.User;
import com.example.fitnessbot.model.UserLanguage;
import com.example.fitnessbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserLanguageServiceTest {

    private static final Long TELEGRAM_USER_ID = 12345L;

    private UserRepository userRepository;
    private UserLanguageService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserLanguageService(userRepository);
    }

    @Test
    void defaultsToEnglishWhenUserDoesNotExist() {
        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.empty());

        assertThat(service.getLanguage(TELEGRAM_USER_ID)).isEqualTo(UserLanguage.ENGLISH);
    }

    @Test
    void setLanguageCreatesUserWhenMissing() {
        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserLanguage result = service.setLanguage(TELEGRAM_USER_ID, UserLanguage.RUSSIAN);

        assertThat(result).isEqualTo(UserLanguage.RUSSIAN);
        verify(userRepository).save(argThat(user ->
                TELEGRAM_USER_ID.equals(user.getTelegramId())
                        && user.getLanguage() == UserLanguage.RUSSIAN
        ));
    }
}
