package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartProgramCallbackHandlerTest {

    private static final Long TEST_TELEGRAM_ID = 12345L;
    private static final Long TEST_CHAT_ID = 6789L;

    @Mock
    private ProgramService programService;

    private StartProgramCallbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StartProgramCallbackHandler(programService);
    }

    @Test
    void testCanHandle() {
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        when(callbackQuery.getData()).thenReturn("start_program:1");

        assertThat(handler.canHandle(callbackQuery)).isTrue();
    }

    @Test
    void testHandleSuccess() throws Exception {
        Update update = createUpdate("start_program:1");
        Program program = new Program();
        program.setName("Strength");
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setTitle("Upper Body");

        when(programService.startProgramForUser(1L, TEST_TELEGRAM_ID))
                .thenReturn(new ProgramService.ActiveProgramSelection(program, trainingDay, 1));

        SendMessage response = handler.handle(update);

        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("Started program \"Strength\"");
        assertThat(response.getText()).contains("Week 1, active training day: Upper Body");
        assertThat(response.getText()).contains("/active_day");
    }

    @Test
    void testHandleError() throws Exception {
        Update update = createUpdate("start_program:1");
        when(programService.startProgramForUser(1L, TEST_TELEGRAM_ID))
                .thenThrow(new ProgramException("Cannot start a program without training days."));

        SendMessage response = handler.handle(update);

        assertThat(response.getText()).isEqualTo("Cannot start a program without training days.");
    }

    private Update createUpdate(String callbackData) {
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(callbackQuery.getFrom()).thenReturn(user);
        when(message.getChatId()).thenReturn(TEST_CHAT_ID);
        when(user.getId()).thenReturn(TEST_TELEGRAM_ID);
        return update;
    }
}
