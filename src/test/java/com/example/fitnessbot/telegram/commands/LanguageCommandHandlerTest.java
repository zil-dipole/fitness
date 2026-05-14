package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.UserLanguage;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LanguageCommandHandlerTest {

    private static final Long TELEGRAM_USER_ID = 12345L;
    private static final Long CHAT_ID = 6789L;

    private UserLanguageService languageService;
    private MenuKeyboardFactory menuKeyboardFactory;
    private LanguageCommandHandler handler;

    @BeforeEach
    void setUp() {
        languageService = mock(UserLanguageService.class);
        menuKeyboardFactory = mock(MenuKeyboardFactory.class);
        handler = new LanguageCommandHandler(languageService, menuKeyboardFactory);
    }

    @Test
    void languageWithoutArgumentShowsLanguageButtons() {
        when(languageService.getLanguage(TELEGRAM_USER_ID)).thenReturn(UserLanguage.ENGLISH);

        SendMessage response = handler.handle(update("/language"));

        assertThat(response.getText()).contains("Current language: English");
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        assertThat(markup.getKeyboard().getFirst()).extracting(InlineKeyboardButton::getText)
                .containsExactly("English", "Русский");
    }

    @Test
    void languageArgumentUpdatesUserLanguage() {
        when(languageService.getLanguage(TELEGRAM_USER_ID)).thenReturn(UserLanguage.ENGLISH);
        when(languageService.setLanguage(TELEGRAM_USER_ID, UserLanguage.RUSSIAN)).thenReturn(UserLanguage.RUSSIAN);

        SendMessage response = handler.handle(update("/language ru"));

        assertThat(response.getText()).isEqualTo("✅ Язык изменён на русский.");
        verify(languageService).setLanguage(TELEGRAM_USER_ID, UserLanguage.RUSSIAN);
        verify(menuKeyboardFactory).createMainMenuKeyboard(TELEGRAM_USER_ID);
    }

    private Update update(String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(TELEGRAM_USER_ID);
        return update;
    }
}
