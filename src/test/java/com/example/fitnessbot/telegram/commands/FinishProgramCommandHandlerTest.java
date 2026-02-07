package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.ProgramTrainingDay;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.ArrayList;
import java.util.List;

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
        assertFalse(handler.canHandle("/create_program"));
        assertFalse(handler.canHandle("/start"));
    }

    @Test
    void testHandleWithoutActiveSession() {
        // Given
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);
        
        long userId = 12345L;
        when(sessionManager.hasActiveSession(userId)).thenReturn(false);
        lenient().when(update.hasMessage()).thenReturn(true);
        lenient().when(update.getMessage()).thenReturn(message);
        long chatId = 6789L;
        lenient().when(message.getChatId()).thenReturn(chatId);
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(userId);
        
        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals(String.valueOf(chatId), response.getChatId());
        assertTrue(response.getText().contains("You don't have an active program creation session"));
    }

    @Test
    void testHandleWithEmptyTrainingDays() {
        // Given
        Update update = createMockUpdateWithCommand("/finish_program");
        when(sessionManager.hasActiveSession(12345L)).thenReturn(true);
        
        Program program = new Program();
        program.setId(1L);
        program.setName("My Program");
        
        ProgramCreationSessionManager.ProgramCreationSession session = 
                mock(ProgramCreationSessionManager.ProgramCreationSession.class);
        when(session.getProgram()).thenReturn(program);
        when(session.getTrainingDays()).thenReturn(new ArrayList<>()); // Empty list
        when(sessionManager.getSession(12345L)).thenReturn(session);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("No training days were added to your program"));
    }

    @Test
    void testHandleSuccess() {
        // Given
        Update update = createMockUpdateWithCommand("/finish_program");
        when(sessionManager.hasActiveSession(12345L)).thenReturn(true);

        Program program = new Program();
        program.setId(1L);
        program.setName("My Program");

        TrainingDay trainingDay1 = new TrainingDay();
        trainingDay1.setId(10L);

        TrainingDay trainingDay2 = new TrainingDay();
        trainingDay2.setId(11L);

        List<TrainingDay> trainingDays = List.of(trainingDay1, trainingDay2);

        ProgramCreationSessionManager.ProgramCreationSession session =
                mock(ProgramCreationSessionManager.ProgramCreationSession.class);
        when(session.getProgram()).thenReturn(program);
        when(session.getTrainingDays()).thenReturn(trainingDays);
        when(sessionManager.getSession(12345L)).thenReturn(session);

        ProgramTrainingDay programTrainingDay1 = new ProgramTrainingDay();
        ProgramTrainingDay programTrainingDay2 = new ProgramTrainingDay();
        
        when(programService.addTrainingDayToProgram(1L, 10L, 1)).thenReturn(programTrainingDay1);
        when(programService.addTrainingDayToProgram(1L, 11L, 2)).thenReturn(programTrainingDay2);
        doNothing().when(sessionManager).endSession(12345L);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("Program \"My Program\" created successfully!"));
        assertTrue(response.getText().contains("Added 2 training days to the program"));

        verify(programService).addTrainingDayToProgram(1L, 10L, 1);
        verify(programService).addTrainingDayToProgram(1L, 11L, 2);
        verify(sessionManager).endSession(12345L);
    }

    @Test
    void testHandleWithError() {
        // Given
        Update update = createMockUpdateWithCommand("/finish_program");
        when(sessionManager.hasActiveSession(12345L)).thenReturn(true);

        Program program = new Program();
        program.setId(1L);
        program.setName("My Program");

        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(10L);

        List<TrainingDay> trainingDays = List.of(trainingDay);

        ProgramCreationSessionManager.ProgramCreationSession session =
                mock(ProgramCreationSessionManager.ProgramCreationSession.class);
        when(session.getProgram()).thenReturn(program);
        when(session.getTrainingDays()).thenReturn(trainingDays);
        when(sessionManager.getSession(12345L)).thenReturn(session);

        when(programService.addTrainingDayToProgram(1L, 10L, 1))
                .thenThrow(new RuntimeException("Database error"));

        // When
        SendMessage response = handler.handle(update);
        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("Sorry, there was an error finishing program creation"));

        verify(programService).addTrainingDayToProgram(1L, 10L, 1);
        verify(sessionManager, never()).endSession(anyLong());
    }

    private Update createMockUpdateWithCommand(String command) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);
        Chat chat = mock(Chat.class);

        lenient().when(update.hasMessage()).thenReturn(true);
        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.hasText()).thenReturn(true);
        lenient().when(message.getText()).thenReturn(command);
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(12345L);
        lenient().when(message.getChatId()).thenReturn(6789L);

        return update;
    }
}