package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.User;
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


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateProgramCommandHandlerTest {

    @Mock
    private ProgramService programService;

    @Mock
    private ProgramCreationSessionManager sessionManager;

    private CreateProgramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateProgramCommandHandler(programService, sessionManager);
    }

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle("/create_program"));
        assertTrue(handler.canHandle("/create_program My Program"));
        assertFalse(handler.canHandle("/start"));
        assertFalse(handler.canHandle("/help"));
    }

    @Test
    void testHandleWithActiveSession() {
        // Given
        Update update = createMockUpdateWithCommand("/create_program My Program");
        when(sessionManager.hasActiveSession(12345L)).thenReturn(true);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("You already have an active program creation session"));
    }

    @Test
    void testHandleSuccessWithName() {
        // Given
        Update update = createMockUpdateWithCommand("/create_program My Awesome Program");
        when(sessionManager.hasActiveSession(12345L)).thenReturn(false);
        
        Program program = new Program();
        program.setId(1L);
        program.setName("My Awesome Program");
        User user = new User();
        user.setId(1L);
        program.setUser(user);
        
        when(programService.startProgramCreation(12345L, "My Awesome Program")).thenReturn(program);
        doNothing().when(sessionManager).startSession(12345L, program);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("Started creating program: \"My Awesome Program\""));
        assertTrue(response.getText().contains("/finish_program to complete the process"));

        verify(programService).startProgramCreation(12345L, "My Awesome Program");
        verify(sessionManager).startSession(12345L, program);
    }

    @Test
    void testHandleSuccessWithoutName() {
        // Given
        Update update = createMockUpdateWithCommand("/create_program");
        when(sessionManager.hasActiveSession(12345L)).thenReturn(false);
        
        Program program = new Program();
        program.setId(1L);
        program.setName("My Program");
        User user = new User();
        user.setId(1L);
        program.setUser(user);
        
        when(programService.startProgramCreation(12345L, "My Program")).thenReturn(program);
        doNothing().when(sessionManager).startSession(12345L, program);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("Started creating program: \"My Program\""));

        verify(programService).startProgramCreation(12345L, "My Program");
        verify(sessionManager).startSession(12345L, program);
    }

    @Test
    void testHandleWithError() {
        // Given
        Update update = createMockUpdateWithCommand("/create_program Test Program");
        when(sessionManager.hasActiveSession(12345L)).thenReturn(false);
        when(programService.startProgramCreation(12345L, "Test Program"))
                .thenThrow(new RuntimeException("Database error"));

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("Sorry, there was an error starting program creation"));

        verify(programService).startProgramCreation(12345L, "Test Program");
        verify(sessionManager, never()).startSession(anyLong(), any());
    }

    private Update createMockUpdateWithCommand(String command) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getText()).thenReturn(command);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(12345L);
        when(message.getChatId()).thenReturn(6789L);

        return update;
    }
}