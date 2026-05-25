package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateProgramCommandHandlerTest {
    
    private static final Long TEST_USER_ID = 12345L;
    private static final Long TEST_CHAT_ID = 6789L;

    @Mock
    private ProgramService programService;

    @Mock
    private ProgramCreationSessionManager sessionManager;

    private CreateProgramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateProgramCommandHandler(programService, sessionManager, mock(MenuKeyboardFactory.class));
    }

    @Test
    void testCanHandle() {
        assertThat(handler.canHandle("/create_program")).isTrue();
        assertThat(handler.canHandle("/create_program My Program")).isTrue();
        assertThat(handler.canHandle("/start")).isFalse();
        assertThat(handler.canHandle("/help")).isFalse();
    }

    @Test
    void testHandleWithActiveSession() throws Exception {
        // Given
        Update update = createMockUpdateWithCommand("/create_program My Program");
        when(sessionManager.hasProgramCreationInProgress(TEST_USER_ID)).thenReturn(true);
        when(sessionManager.isAwaitingProgramName(TEST_USER_ID)).thenReturn(false);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("You already have a program draft in progress");
        
        verify(sessionManager).hasProgramCreationInProgress(TEST_USER_ID);
        verify(sessionManager).isAwaitingProgramName(TEST_USER_ID);
        verifyNoMoreInteractions(programService, sessionManager);
    }

    @Test
    void testHandleUnavailableWhileAwaitingProgramNamePromptsAgain() {
        Update update = createMockUpdateForUnavailable();
        when(sessionManager.isAwaitingProgramName(TEST_USER_ID)).thenReturn(true);

        SendMessage response = handler.handleUnavailable(update);

        assertThat(response.getText()).contains("What should this program be called?");
        verify(sessionManager).isAwaitingProgramName(TEST_USER_ID);
        verifyNoMoreInteractions(programService, sessionManager);
    }

    @Test
    void testHandleSuccessWithName() throws Exception {
        // Given
        Update update = createMockUpdateWithCommand("/create_program My Awesome Program");
        when(sessionManager.hasProgramCreationInProgress(TEST_USER_ID)).thenReturn(false);

        Program program = new Program();
        program.setId(1L);
        program.setName("My Awesome Program");
        User user = new User();
        user.setId(1L);
        program.setUser(user);

        when(programService.startProgramCreation(TEST_USER_ID, "My Awesome Program")).thenReturn(program);
        doNothing().when(sessionManager).startSession(TEST_USER_ID, program);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("Program draft created: \"My Awesome Program\"");
        assertThat(response.getText()).contains("Send or forward the training day messages");
        assertThat(response.getText()).contains("tap \"Finish Program Creation\" or send /finish_program");

        verify(programService).startProgramCreation(TEST_USER_ID, "My Awesome Program");
        verify(sessionManager).hasProgramCreationInProgress(TEST_USER_ID);
        verify(sessionManager).startSession(TEST_USER_ID, program);
        verifyNoMoreInteractions(programService, sessionManager);
    }

    @Test
    void testHandleSuccessWithoutName() throws Exception {
        // Given
        Update update = createMockUpdateWithCommand("/create_program");
        when(sessionManager.hasProgramCreationInProgress(TEST_USER_ID)).thenReturn(false);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("What should this program be called?");

        verify(sessionManager).hasProgramCreationInProgress(TEST_USER_ID);
        verify(sessionManager).startAwaitingProgramName(TEST_USER_ID);
        verifyNoInteractions(programService);
        verifyNoMoreInteractions(programService, sessionManager);
    }

    @Test
    void testHandleWithError() throws Exception {
        // Given
        Update update = createMockUpdateWithCommand("/create_program Test Program");
        when(sessionManager.hasProgramCreationInProgress(TEST_USER_ID)).thenReturn(false);
        when(programService.startProgramCreation(TEST_USER_ID, "Test Program"))
                .thenThrow(new RuntimeException("Database error"));

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("Sorry, there was an error starting program creation");

        verify(programService).startProgramCreation(TEST_USER_ID, "Test Program");
        verify(sessionManager).hasProgramCreationInProgress(TEST_USER_ID);
        verify(sessionManager, never()).startSession(anyLong(), any());
        verifyNoMoreInteractions(programService, sessionManager);
    }

    private Update createMockUpdateWithCommand(String command) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getText()).thenReturn(command);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(TEST_USER_ID);
        when(message.getChatId()).thenReturn(TEST_CHAT_ID);

        return update;
    }

    private Update createMockUpdateForUnavailable() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(TEST_USER_ID);
        when(message.getChatId()).thenReturn(TEST_CHAT_ID);

        return update;
    }
}
