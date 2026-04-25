package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.ProgramTrainingDay;
import com.example.fitnessbot.model.TrainingDay;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowProgramCommandHandlerTest {
    
    private static final Long TEST_TELEGRAM_ID = 12345L;
    private static final Long TEST_CHAT_ID = 6789L;

    @Mock
    private ProgramService programService;

    @Mock
    private ProgramCreationSessionManager sessionManager;

    private ShowProgramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ShowProgramCommandHandler(programService, sessionManager);
    }

    @Test
    void testCanHandle() {
        assertThat(handler.canHandle("/show_program")).isTrue();
        assertThat(handler.canHandle("/start")).isFalse();
        assertThat(handler.canHandle("/help")).isFalse();
        assertThat(handler.canHandle("/create_program")).isFalse();
    }

    @Test
    void testIsAvailableWithActiveSession() {
        when(sessionManager.hasActiveSession(TEST_TELEGRAM_ID)).thenReturn(true);
        assertThat(handler.isAvailable(TEST_TELEGRAM_ID, sessionManager)).isTrue();
    }

    @Test
    void testIsAvailableWithoutActiveProgramOrSession() {
        when(sessionManager.hasActiveSession(TEST_TELEGRAM_ID)).thenReturn(false);
        assertThat(handler.isAvailable(TEST_TELEGRAM_ID, sessionManager)).isFalse();
    }

    @Test
    void testHandleUnavailable() {
        Update update = createMockUpdate();
        SendMessage response = handler.handleUnavailable(update);

        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).isEqualTo("You don't have an active program creation session. Start one with /create_program <name>");
        
        // Verify that no unexpected interactions occurred
        verifyNoMoreInteractions(sessionManager);
    }

    @Test
    void testHandleWithoutActiveProgram() {
        // Given
        Update update = createMockUpdate();
        when(sessionManager.getSession(TEST_TELEGRAM_ID)).thenReturn(null);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("You don't have an active program creation session");
        assertThat(response.getParseMode()).isNull(); // No markdown for plain text
        assertThat(response.getReplyMarkup()).isNull(); // No keyboard for this case

        // Verify interactions
        verify(sessionManager).getSession(TEST_TELEGRAM_ID);
        verifyNoMoreInteractions(sessionManager);
    }

    @Test
    void testHandleWithActiveProgramButNoTrainingDays() {
        // Given
        Update update = createMockUpdate();

        Program program = new Program();
        program.setId(1L);
        program.setName("My Workout Program");
        User user = new User();
        user.setId(1L);
        program.setUser(user);

        ProgramCreationSessionManager.ProgramCreationSession session = mock(ProgramCreationSessionManager.ProgramCreationSession.class);
        when(session.getProgram()).thenReturn(program);
        when(session.getTrainingDays()).thenReturn(Collections.emptyList());

        when(sessionManager.getSession(TEST_TELEGRAM_ID)).thenReturn(session);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getParseMode()).isEqualTo("Markdown");
        assertThat(response.getText()).contains("*Program Creation Session: My Workout Program*");
        assertThat(response.getText()).contains("No training days added yet.");

        // Verify interactions
        verify(sessionManager).getSession(TEST_TELEGRAM_ID);
        verifyNoMoreInteractions(sessionManager);
    }

    @Test
    void testHandleWithActiveProgramAndTrainingDays() {
        // Given
        Update update = createMockUpdate();

        Program program = new Program();
        program.setId(1L);
        program.setName("My Workout Program");
        User user = new User();
        user.setId(1L);
        program.setUser(user);

        TrainingDay td1 = new TrainingDay();
        td1.setId(1L);
        td1.setTitle("Upper Body");

        TrainingDay td2 = new TrainingDay();
        td2.setId(2L);
        td2.setTitle("Lower Body");

        ProgramCreationSessionManager.ProgramCreationSession session = mock(ProgramCreationSessionManager.ProgramCreationSession.class);
        when(session.getProgram()).thenReturn(program);
        when(session.getTrainingDays()).thenReturn(Arrays.asList(td1, td2));

        when(sessionManager.getSession(TEST_TELEGRAM_ID)).thenReturn(session);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getParseMode()).isEqualTo("Markdown");
        assertThat(response.getText()).contains("*Program Creation Session: My Workout Program*");
        assertThat(response.getText()).contains("Training Days Added:");
        assertThat(response.getText()).contains("- Upper Body");
        assertThat(response.getText()).contains("- Lower Body");

        // Verify interactions
        verify(sessionManager).getSession(TEST_TELEGRAM_ID);
        verifyNoMoreInteractions(sessionManager);
    }

    private Update createMockUpdate() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.getText()).thenReturn("/show_program");
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(TEST_TELEGRAM_ID);
        lenient().when(message.getChatId()).thenReturn(TEST_CHAT_ID);

        return update;
    }
}