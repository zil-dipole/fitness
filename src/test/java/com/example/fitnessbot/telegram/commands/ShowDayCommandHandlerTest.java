package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.service.TrainingDayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowDayCommandHandlerTest {
    
    private static final Long TEST_TELEGRAM_ID = 12345L;
    private static final Long TEST_CHAT_ID = 6789L;
    private static final Long UNAUTHORIZED_TELEGRAM_ID = 99999L;

    @Mock
    private TrainingDayService trainingDayService;

    private ShowDayCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ShowDayCommandHandler(trainingDayService);
    }

    @Test
    void testCanHandleValidCallbackData() {
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        when(callbackQuery.getData()).thenReturn("show_day_123");

        assertThat(handler.canHandle(callbackQuery)).isTrue();
    }

    @Test
    void testCanHandleInvalidCallbackData() {
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        when(callbackQuery.getData()).thenReturn("invalid_data");

        assertThat(handler.canHandle(callbackQuery)).isFalse();
    }

    @Test
    void testHandleWithValidTrainingDay() {
        // Given
        Update update = createMockUpdate("show_day_1");

        // Create training day with exercises
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(1L);
        trainingDay.setTitle("Upper <Body> & Arms");
        trainingDay.setRawText("Upper Body\nUpper body workout focusing on chest & shoulders\n\n- Bench Press 3 x 10 (Warm up)\n- https://youtube.com/watch?v=example&list=test");

        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        trainingDay.setUser(user);

        // Create exercise with sets
        Exercise exercise = new Exercise();
        exercise.setId(1L);
        exercise.setName("Bench & Press > Row");
        exercise.setSets(3);
        exercise.setRepsOrDuration("10");
        exercise.setLastWeightKg(60.0);
        exercise.setNotes("(Warm up) keep shoulders < elbows");
        exercise.setVideoUrls(Arrays.asList("https://youtube.com/watch?v=example&list=test"));
        exercise.setTrainingDay(trainingDay);

        trainingDay.setExercises(Collections.singletonList(exercise));

        when(trainingDayService.getTrainingDayById(1L)).thenReturn(trainingDay);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getParseMode()).isEqualTo("HTML");
        assertThat(response.getText()).contains("<blockquote>Upper Body\nUpper body workout focusing on chest &amp; shoulders");
        assertThat(response.getText()).contains("<b>Upper &lt;Body&gt; &amp; Arms</b>");
        assertThat(response.getText()).contains("Upper body workout focusing on chest &amp; shoulders");
        assertThat(response.getText()).contains("1. Bench &amp; Press &gt; Row");
        assertThat(response.getText()).contains("3 x 10");
        assertThat(response.getText()).contains("@ 60.0 kg");
        assertThat(response.getText()).contains("Notes: (Warm up) keep shoulders &lt; elbows");
        assertThat(response.getText()).contains("https://youtube.com/watch?v=example&amp;list=test");

        verify(trainingDayService).getTrainingDayById(1L);
        verifyNoMoreInteractions(trainingDayService);
    }

    @Test
    void testHandleWithTrainingDayNotFound() {
        // Given
        Update update = createMockUpdate("show_day_999");
        when(trainingDayService.getTrainingDayById(999L)).thenReturn(null);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).isEqualTo("Training day not found.");
        
        verify(trainingDayService).getTrainingDayById(999L);
        verifyNoMoreInteractions(trainingDayService);
    }

    @Test
    void testHandleWithInvalidTrainingDayId() {
        // Given
        Update update = createMockUpdate("show_day_invalid");

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).isEqualTo("Invalid training day ID.");
    }

    @Test
    void testHandleWithUnauthorizedAccess() {
        // Given
        Update update = createMockUpdate("show_day_1");

        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(1L);
        trainingDay.setTitle("Upper Body");

        User user = new User();
        user.setId(1L);
        user.setTelegramId(UNAUTHORIZED_TELEGRAM_ID); // Different user ID
        trainingDay.setUser(user);

        when(trainingDayService.getTrainingDayById(1L)).thenReturn(trainingDay);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).isEqualTo("You don't have permission to view this training day.");
        
        verify(trainingDayService).getTrainingDayById(1L);
        verifyNoMoreInteractions(trainingDayService);
    }

    private Update createMockUpdate(String callbackData) {
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(TEST_CHAT_ID);
        when(callbackQuery.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(TEST_TELEGRAM_ID);

        return update;
    }
}
