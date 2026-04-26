package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveDayCommandHandlerTest {

    private static final Long TEST_TELEGRAM_ID = 12345L;
    private static final Long TEST_CHAT_ID = 6789L;

    @Mock
    private ProgramService programService;

    private ActiveDayCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ActiveDayCommandHandler(programService);
    }

    @Test
    void testCanHandle() {
        assertThat(handler.canHandle("/active_day")).isTrue();
        assertThat(handler.canHandle("/show_program")).isFalse();
    }

    @Test
    void testHandleWithoutActiveProgram() {
        Update update = createUpdate();
        when(programService.getActiveTrainingDayForUser(TEST_TELEGRAM_ID)).thenReturn(null);

        SendMessage response = handler.handle(update);

        assertThat(response.getText()).contains("You don't have an active program");
    }

    @Test
    void testHandleWithActiveTrainingDay() {
        Update update = createUpdate();
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setTitle("Upper <Body> & Arms");
        Exercise exercise = new Exercise();
        exercise.setName("Bench & Row > Press");
        exercise.setNotes("Keep elbows < shoulders & controlled");
        trainingDay.setExercises(List.of(exercise));

        when(programService.getActiveTrainingDayForUser(TEST_TELEGRAM_ID)).thenReturn(trainingDay);

        SendMessage response = handler.handle(update);

        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getParseMode()).isEqualTo("HTML");
        assertThat(response.getText()).contains("Active training day:");
        assertThat(response.getText()).contains("<b>Upper &lt;Body&gt; &amp; Arms</b>");
        assertThat(response.getText()).contains("Bench &amp; Row &gt; Press");
        assertThat(response.getText()).contains("Keep elbows &lt; shoulders &amp; controlled");
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
    }

    @Test
    void testHandleShowsAdvancedTrainingDay() {
        Update update = createUpdate();
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setTitle("Lower Body");
        Exercise exercise = new Exercise();
        exercise.setName("Squat");
        trainingDay.setExercises(List.of(exercise));

        when(programService.getActiveTrainingDayForUser(TEST_TELEGRAM_ID)).thenReturn(trainingDay);

        SendMessage response = handler.handle(update);

        assertThat(response.getText()).contains("Lower Body");
        assertThat(response.getText()).contains("Squat");
    }

    private Update createUpdate() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getFrom()).thenReturn(user);
        when(message.getChatId()).thenReturn(TEST_CHAT_ID);
        when(user.getId()).thenReturn(TEST_TELEGRAM_ID);
        return update;
    }
}
