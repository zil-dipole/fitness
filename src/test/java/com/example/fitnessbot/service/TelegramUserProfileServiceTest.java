package com.example.fitnessbot.service;

import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramUserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void recordTelegramUserCreatesUserWithNormalizedUsername() {
        TelegramUserProfileService service = new TelegramUserProfileService(userRepository);
        org.telegram.telegrambots.meta.api.objects.User telegramUser = new org.telegram.telegrambots.meta.api.objects.User();
        telegramUser.setId(123L);
        telegramUser.setUserName("MGhostL");

        when(userRepository.findByTelegramId(123L)).thenReturn(Optional.empty());

        service.recordTelegramUser(telegramUser);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getTelegramId()).isEqualTo(123L);
        assertThat(userCaptor.getValue().getTelegramUsername()).isEqualTo("mghostl");
    }

    @Test
    void recordTelegramUserUpdatesChangedUsername() {
        TelegramUserProfileService service = new TelegramUserProfileService(userRepository);
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setTelegramId(123L);
        existingUser.setTelegramUsername("old_login");

        org.telegram.telegrambots.meta.api.objects.User telegramUser = new org.telegram.telegrambots.meta.api.objects.User();
        telegramUser.setId(123L);
        telegramUser.setUserName("New_Login");

        when(userRepository.findByTelegramId(123L)).thenReturn(Optional.of(existingUser));

        service.recordTelegramUser(telegramUser);

        verify(userRepository).save(existingUser);
        assertThat(existingUser.getTelegramUsername()).isEqualTo("new_login");
    }

    @Test
    void recordTelegramUserDoesNotSaveWhenUsernameIsUnchanged() {
        TelegramUserProfileService service = new TelegramUserProfileService(userRepository);
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setTelegramId(123L);
        existingUser.setTelegramUsername("mghostl");

        org.telegram.telegrambots.meta.api.objects.User telegramUser = new org.telegram.telegrambots.meta.api.objects.User();
        telegramUser.setId(123L);
        telegramUser.setUserName("MGhostL");

        when(userRepository.findByTelegramId(123L)).thenReturn(Optional.of(existingUser));

        service.recordTelegramUser(telegramUser);

        verify(userRepository, never()).save(existingUser);
    }
}
