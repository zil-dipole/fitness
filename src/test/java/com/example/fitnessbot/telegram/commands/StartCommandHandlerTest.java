package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartCommandHandlerTest {
    
    private static final Long TEST_TELEGRAM_ID = 12345L;
    private static final Long TEST_CHAT_ID = 6789L;

    @Mock
    private ProgramCreationSessionManager sessionManager;
    
    @Mock
    private MenuKeyboardFactory menuKeyboardFactory;

    private StartCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StartCommandHandler(menuKeyboardFactory);
    }

    @Test
    void testCanHandle() {
        assertThat(handler.canHandle("/start")).isTrue();
        assertThat(handler.canHandle("/help")).isFalse();
        assertThat(handler.canHandle("/create_program")).isFalse();
    }

    @Test
    void testIsAvailableWithoutActiveSession() {
        when(sessionManager.hasActiveSession(TEST_TELEGRAM_ID)).thenReturn(false);
        assertThat(handler.isAvailable(TEST_TELEGRAM_ID, sessionManager)).isTrue();
    }

    @Test
    void testIsAvailableWithActiveSession() {
        when(sessionManager.hasActiveSession(TEST_TELEGRAM_ID)).thenReturn(true);
        assertThat(handler.isAvailable(TEST_TELEGRAM_ID, sessionManager)).isFalse();
    }

    @Test
    void testHandleUnavailable() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(TEST_CHAT_ID);

        SendMessage response = handler.handleUnavailable(update);

        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("You're already using the bot");
    }

    @Test
    void testHandleSuccess() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        when(update.getMessage()).thenReturn(message);
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(TEST_TELEGRAM_ID);
        when(message.getChatId()).thenReturn(TEST_CHAT_ID);
        when(menuKeyboardFactory.createMainMenuKeyboard(TEST_TELEGRAM_ID)).thenReturn(startMenuMarkup());

        SendMessage response = handler.handle(update);

        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("Welcome to Fitness Bot");
        assertThat(response.getText()).contains("Forward your workout programs");

        // Check that inline keyboard is present
        assertThat(response.getReplyMarkup()).isNotNull();
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = markup.getKeyboard();

        // Check that we have one row with one button
        assertThat(keyboard).hasSize(1);
        assertThat(keyboard.get(0)).hasSize(1);

        // Check button text and callback data
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton button = keyboard.get(0).get(0);
        assertThat(button.getText()).isEqualTo("Open Menu");
        assertThat(button.getCallbackData()).isEqualTo("start_menu");
        
        // Verify that no unexpected interactions occurred
        verifyNoMoreInteractions(sessionManager);
    }

    private InlineKeyboardMarkup startMenuMarkup() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("Open Menu");
        button.setCallbackData("start_menu");
        markup.setKeyboard(List.of(List.of(button)));
        return markup;
    }
}
