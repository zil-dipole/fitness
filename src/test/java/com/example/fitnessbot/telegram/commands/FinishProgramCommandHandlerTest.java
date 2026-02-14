package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
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
class FinishProgramCommandHandlerTest {

    @Mock
    private ProgramService programService;

    @Mock
    private ProgramCreationSessionManager sessionManager;

    private FinishProgramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FinishProgramCommandHandler(programService, sessionManager);
    }

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle("/finish_program"));
        assertFalse(handler.canHandle("/start"));
        assertFalse(handler.canHandle("/help"));
        assertFalse(handler.canHandle("/create_program"));
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
        Update update = createMockUpdate();
        SendMessage response = handler.handleUnavailable(update);
        
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertEquals("You don't have an active program creation session. Start one with /create_program <program_name>", response.getText());
    }

    @Test
    void testHandleWithoutActiveSession() {
        Update update = createMockUpdate();
        when(sessionManager.hasActiveSession(12345L)).thenReturn(false);

        SendMessage response = handler.handle(update);

        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertEquals("You don't have an active program creation session. Start one with /create_program <program_name>", response.getText());
        
        verify(sessionManager).hasActiveSession(12345L);
        verifyNoMoreInteractions(sessionManager);
    }

    private Update createMockUpdate() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.getText()).thenReturn("/finish_program");
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(12345L);
        lenient().when(message.getChatId()).thenReturn(6789L);

        return update;
    }
}