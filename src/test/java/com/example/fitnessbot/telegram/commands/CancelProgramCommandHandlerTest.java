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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelProgramCommandHandlerTest {

    @Mock
    private ProgramCreationSessionManager sessionManager;

    private CancelProgramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CancelProgramCommandHandler(sessionManager);
    }

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle("/cancel_program"));
        assertFalse(handler.canHandle("/create_program"));
        assertFalse(handler.canHandle("/finish_program"));
        assertFalse(handler.canHandle("/start"));
    }

    @Test
    void testIsAvailableWithActiveSession() {
        when(sessionManager.hasActiveSession(12345L)).thenReturn(true);
        assertTrue(handler.isAvailable(12345L, sessionManager));
    }

    @Test
    void testIsAvailableWithoutActiveSession() {
        when(sessionManager.hasActiveSession(12345L)).thenReturn(false);
        assertFalse(handler.isAvailable(12345L, sessionManager));
    }

    @Test
    void testHandleUnavailable() {
        Update update = createMockUpdateWithCommand();
        SendMessage response = handler.handleUnavailable(update);
        
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertEquals("You don't have an active program creation session to cancel.", response.getText());
    }

    @Test
    void testHandleWithoutActiveSession() {
        // Given
        Update update = createMockUpdateWithCommand();
        when(sessionManager.hasActiveSession(12345L)).thenReturn(false);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("You don't have an active program creation session to cancel"));
    }

    @Test
    void testHandleSuccess() {
        // Given
        Update update = createMockUpdateWithCommand();
        when(sessionManager.hasActiveSession(12345L)).thenReturn(true);
        doNothing().when(sessionManager).endSession(12345L);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("Program creation cancelled"));

        verify(sessionManager).endSession(12345L);
    }

    private Update createMockUpdateWithCommand() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        lenient().when(update.hasMessage()).thenReturn(true);
        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.hasText()).thenReturn(true);
        lenient().when(message.getText()).thenReturn("/cancel_program");
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(12345L);
        lenient().when(message.getChatId()).thenReturn(6789L);

        return update;
    }
}