package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartCommandHandlerTest {

    @Mock
    private ProgramCreationSessionManager sessionManager;

    private StartCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StartCommandHandler();
    }

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle("/start"));
        assertFalse(handler.canHandle("/help"));
        assertFalse(handler.canHandle("/create_program"));
    }

    @Test
    void testIsAvailableWithoutActiveSession() {
        when(sessionManager.hasActiveSession(12345L)).thenReturn(false);
        assertTrue(handler.isAvailable(12345L, sessionManager));
    }

    @Test
    void testIsAvailableWithActiveSession() {
        when(sessionManager.hasActiveSession(12345L)).thenReturn(true);
        assertFalse(handler.isAvailable(12345L, sessionManager));
    }

    @Test
    void testHandleUnavailable() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(6789L);
        
        SendMessage response = handler.handleUnavailable(update);

        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("You're already using the bot"));
    }

    @Test
    void testHandleSuccess() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(12345L);
        when(message.getChatId()).thenReturn(6789L);
        
        SendMessage response = handler.handle(update);

        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("Welcome to Fitness Bot"));
        assertTrue(response.getText().contains("Forward your workout programs"));

        // Check that inline keyboard is present
        assertNotNull(response.getReplyMarkup());
        assertTrue(response.getReplyMarkup() instanceof InlineKeyboardMarkup);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = markup.getKeyboard();

        // Check that we have one row with one button
        assertEquals(1, keyboard.size());
        assertEquals(1, keyboard.get(0).size());

        // Check button text and callback data
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton button = keyboard.get(0).get(0);
        assertEquals("Open Menu", button.getText());
        assertEquals("start_menu", button.getCallbackData());
    }
}