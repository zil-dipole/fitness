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
        assertThat(handler.canHandle("/show_program 1")).isTrue();
        assertThat(handler.canHandle("/start")).isFalse();
        assertThat(handler.canHandle("/help")).isFalse();
        assertThat(handler.canHandle("/create_program")).isFalse();
    }

    @Test
    void testIsAvailableWithActiveSession() {
        assertThat(handler.isAvailable(TEST_TELEGRAM_ID, sessionManager)).isTrue();
    }

    @Test
    void testIsAvailableWithoutActiveProgramOrSession() {
        assertThat(handler.isAvailable(TEST_TELEGRAM_ID, sessionManager)).isTrue();
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
    void testHandleWithoutSavedPrograms() {
        // Given
        Update update = createMockUpdate();
        when(sessionManager.getSession(TEST_TELEGRAM_ID)).thenReturn(null);
        when(programService.getProgramsForUser(TEST_TELEGRAM_ID)).thenReturn(Collections.emptyList());

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("You don't have any saved programs yet");
        assertThat(response.getParseMode()).isNull(); // No markdown for plain text
        assertThat(response.getReplyMarkup()).isNull(); // No keyboard for this case

        // Verify interactions
        verify(sessionManager).getSession(TEST_TELEGRAM_ID);
        verify(programService).getProgramsForUser(TEST_TELEGRAM_ID);
        verifyNoMoreInteractions(sessionManager);
    }

    @Test
    void testHandleWithSavedPrograms() {
        // Given
        Update update = createMockUpdate();
        when(sessionManager.getSession(TEST_TELEGRAM_ID)).thenReturn(null);

        Program program1 = new Program();
        program1.setId(1L);
        program1.setName("Strength");

        Program program2 = new Program();
        program2.setId(2L);
        program2.setName("Hypertrophy");

        when(programService.getProgramsForUser(TEST_TELEGRAM_ID)).thenReturn(List.of(program1, program2));

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("Your saved programs:");
        assertThat(response.getText()).contains("#1 Strength");
        assertThat(response.getText()).contains("#2 Hypertrophy");
        assertThat(response.getText()).contains("/show_program 1");
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        assertThat(markup.getKeyboard()).hasSize(2);
        assertThat(markup.getKeyboard().get(0).get(0).getText()).isEqualTo("#1 Strength");
        assertThat(markup.getKeyboard().get(0).get(0).getCallbackData()).isEqualTo("show_program:1");

        verify(sessionManager).getSession(TEST_TELEGRAM_ID);
        verify(programService).getProgramsForUser(TEST_TELEGRAM_ID);
    }

    @Test
    void testHandleSavedProgramDetails() {
        // Given
        Update update = createMockUpdate("/show_program 1");

        Program program = new Program();
        program.setId(1L);
        program.setName("Strength");

        TrainingDay td1 = new TrainingDay();
        td1.setId(1L);
        td1.setTitle("Upper Body");

        TrainingDay td2 = new TrainingDay();
        td2.setId(2L);
        td2.setTitle("Lower Body");

        ProgramTrainingDay link1 = new ProgramTrainingDay();
        link1.setPosition(1);
        link1.setTrainingDay(td1);

        ProgramTrainingDay link2 = new ProgramTrainingDay();
        link2.setPosition(2);
        link2.setTrainingDay(td2);

        when(programService.getProgramForUser(1L, TEST_TELEGRAM_ID)).thenReturn(java.util.Optional.of(program));
        when(programService.getProgramTrainingDaysForUser(1L, TEST_TELEGRAM_ID)).thenReturn(List.of(link1, link2));

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("Program: Strength");
        assertThat(response.getText()).contains("1. Upper Body");
        assertThat(response.getText()).contains("2. Lower Body");
        assertThat(response.getText()).contains("Total: 2 training days");

        verifyNoInteractions(sessionManager);
        verify(programService).getProgramForUser(1L, TEST_TELEGRAM_ID);
        verify(programService).getProgramTrainingDaysForUser(1L, TEST_TELEGRAM_ID);
    }

    @Test
    void testHandleSavedProgramDetailsWithHashPrefix() {
        // Given
        Update update = createMockUpdate("/show_program #1 Strength");

        Program program = new Program();
        program.setId(1L);
        program.setName("Strength");

        when(programService.getProgramForUser(1L, TEST_TELEGRAM_ID)).thenReturn(java.util.Optional.of(program));
        when(programService.getProgramTrainingDaysForUser(1L, TEST_TELEGRAM_ID)).thenReturn(Collections.emptyList());

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getText()).contains("Program: Strength");
        assertThat(response.getText()).contains("No training days are linked");

        verifyNoInteractions(sessionManager);
        verify(programService).getProgramForUser(1L, TEST_TELEGRAM_ID);
        verify(programService).getProgramTrainingDaysForUser(1L, TEST_TELEGRAM_ID);
    }

    @Test
    void testHandleSavedProgramDetailsByName() {
        // Given
        Update update = createMockUpdate("/show_program Strength Program");

        Program program = new Program();
        program.setId(1L);
        program.setName("Strength Program");

        when(programService.getProgramsForUser(TEST_TELEGRAM_ID)).thenReturn(List.of(program));
        when(programService.getProgramForUser(1L, TEST_TELEGRAM_ID)).thenReturn(java.util.Optional.of(program));
        when(programService.getProgramTrainingDaysForUser(1L, TEST_TELEGRAM_ID)).thenReturn(Collections.emptyList());

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getText()).contains("Program: Strength Program");

        verifyNoInteractions(sessionManager);
        verify(programService).getProgramsForUser(TEST_TELEGRAM_ID);
        verify(programService).getProgramForUser(1L, TEST_TELEGRAM_ID);
        verify(programService).getProgramTrainingDaysForUser(1L, TEST_TELEGRAM_ID);
    }

    @Test
    void testHandleSavedProgramDetailsByNumericLeadingName() {
        // Given
        Update update = createMockUpdate("/show_program 2024 Strength");

        Program program = new Program();
        program.setId(1L);
        program.setName("2024 Strength");

        when(programService.getProgramsForUser(TEST_TELEGRAM_ID)).thenReturn(List.of(program));
        when(programService.getProgramForUser(1L, TEST_TELEGRAM_ID)).thenReturn(java.util.Optional.of(program));
        when(programService.getProgramTrainingDaysForUser(1L, TEST_TELEGRAM_ID)).thenReturn(Collections.emptyList());

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getText()).contains("Program: 2024 Strength");

        verifyNoInteractions(sessionManager);
        verify(programService).getProgramsForUser(TEST_TELEGRAM_ID);
        verify(programService).getProgramForUser(1L, TEST_TELEGRAM_ID);
        verify(programService).getProgramTrainingDaysForUser(1L, TEST_TELEGRAM_ID);
    }

    @Test
    void testHandleSavedProgramDetailsByNumericExpressionName() {
        // Given
        Update update = createMockUpdate("/show_program 5 x 5");

        Program program = new Program();
        program.setId(1L);
        program.setName("5 x 5");

        when(programService.getProgramsForUser(TEST_TELEGRAM_ID)).thenReturn(List.of(program));
        when(programService.getProgramForUser(1L, TEST_TELEGRAM_ID)).thenReturn(java.util.Optional.of(program));
        when(programService.getProgramTrainingDaysForUser(1L, TEST_TELEGRAM_ID)).thenReturn(Collections.emptyList());

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getText()).contains("Program: 5 x 5");

        verifyNoInteractions(sessionManager);
        verify(programService).getProgramsForUser(TEST_TELEGRAM_ID);
        verify(programService).getProgramForUser(1L, TEST_TELEGRAM_ID);
        verify(programService).getProgramTrainingDaysForUser(1L, TEST_TELEGRAM_ID);
    }

    @Test
    void testHandleSavedProgramDetailsByDuplicateNameShowsButtons() {
        // Given
        Update update = createMockUpdate("/show_program My Program");

        Program program1 = new Program();
        program1.setId(1L);
        program1.setName("My Program");

        Program program2 = new Program();
        program2.setId(2L);
        program2.setName("My Program");

        when(programService.getProgramsForUser(TEST_TELEGRAM_ID)).thenReturn(List.of(program1, program2));

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getText()).contains("Multiple programs named \"My Program\" found");
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        assertThat(markup.getKeyboard()).hasSize(2);
        assertThat(markup.getKeyboard().get(1).get(0).getCallbackData()).isEqualTo("show_program:2");

        verifyNoInteractions(sessionManager);
        verify(programService).getProgramsForUser(TEST_TELEGRAM_ID);
        verifyNoMoreInteractions(programService);
    }

    @Test
    void testHandleSavedProgramNotFound() {
        // Given
        Update update = createMockUpdate("/show_program 99");
        when(programService.getProgramForUser(99L, TEST_TELEGRAM_ID)).thenReturn(java.util.Optional.empty());

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getText()).isEqualTo("Program not found.");

        verifyNoInteractions(sessionManager);
        verify(programService).getProgramForUser(99L, TEST_TELEGRAM_ID);
        verifyNoMoreInteractions(programService);
    }

    @Test
    void testHandleInvalidSavedProgramId() {
        // Given
        Update update = createMockUpdate("/show_program #abc");

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getText()).isEqualTo("Invalid program ID. Use /show_program <program_id>.");

        verifyNoInteractions(sessionManager, programService);
    }

    @Test
    void testHandleSavedProgramNameNotFound() {
        // Given
        Update update = createMockUpdate("/show_program Unknown Program");
        when(programService.getProgramsForUser(TEST_TELEGRAM_ID)).thenReturn(Collections.emptyList());

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getText()).isEqualTo("Program not found. Send /show_program to see your saved programs.");

        verifyNoInteractions(sessionManager);
        verify(programService).getProgramsForUser(TEST_TELEGRAM_ID);
        verifyNoMoreInteractions(programService);
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
        return createMockUpdate("/show_program");
    }

    private Update createMockUpdate(String command) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.getText()).thenReturn(command);
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(TEST_TELEGRAM_ID);
        lenient().when(message.getChatId()).thenReturn(TEST_CHAT_ID);

        return update;
    }
}
