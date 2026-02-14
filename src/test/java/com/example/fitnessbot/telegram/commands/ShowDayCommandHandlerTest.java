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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowDayCommandHandlerTest {

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

        assertTrue(handler.canHandle(callbackQuery));
    }

    @Test
    void testCanHandleInvalidCallbackData() {
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        when(callbackQuery.getData()).thenReturn("invalid_data");

        assertFalse(handler.canHandle(callbackQuery));
    }

    @Test
    void testHandleWithValidTrainingDay() {
        // Given
        Update update = createMockUpdate("show_day_1");
        
        // Create training day with exercises
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(1L);
        trainingDay.setTitle("Upper Body");
        trainingDay.setRawText("Upper Body\nUpper body workout focusing on chest and shoulders\n\n- Bench Press 3 x 10 (Warm up)\n- https://youtube.com/watch?v=example");
        
        User user = new User();
        user.setId(1L);
        user.setTelegramId(12345L);
        trainingDay.setUser(user);
        
        // Create exercise with sets
        Exercise exercise = new Exercise();
        exercise.setId(1L);
        exercise.setName("Bench Press");
        exercise.setSets(3);
        exercise.setRepsOrDuration("10");
        exercise.setLastWeightKg(60.0);
        exercise.setNotes("(Warm up)");
        exercise.setVideoUrls(Arrays.asList("https://youtube.com/watch?v=example"));
        exercise.setTrainingDay(trainingDay);
        
        trainingDay.setExercises(Collections.singletonList(exercise));
        
        when(trainingDayService.getTrainingDayById(1L)).thenReturn(trainingDay);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertEquals("Markdown", response.getParseMode());
        assertTrue(response.getText().contains("*Upper Body*"));
        assertTrue(response.getText().contains("Upper body workout focusing on chest and shoulders"));
        assertTrue(response.getText().contains("1. Bench Press"));
        assertTrue(response.getText().contains("3 x 10"));
        assertTrue(response.getText().contains("@ 60.0 kg"));
        assertTrue(response.getText().contains("Notes: (Warm up)"));
        assertTrue(response.getText().contains("https://youtube.com/watch?v=example"));
        
        verify(trainingDayService).getTrainingDayById(1L);
    }

    @Test
    void testHandleWithTrainingDayNotFound() {
        // Given
        Update update = createMockUpdate("show_day_999");
        when(trainingDayService.getTrainingDayById(999L)).thenReturn(null);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertEquals("Training day not found.", response.getText());
        verify(trainingDayService).getTrainingDayById(999L);
    }

    @Test
    void testHandleWithInvalidTrainingDayId() {
        // Given
        Update update = createMockUpdate("show_day_invalid");

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertEquals("Invalid training day ID.", response.getText());
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
        user.setTelegramId(99999L); // Different user ID
        trainingDay.setUser(user);
        
        when(trainingDayService.getTrainingDayById(1L)).thenReturn(trainingDay);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertEquals("You don't have permission to view this training day.", response.getText());
        verify(trainingDayService).getTrainingDayById(1L);
    }

    private Update createMockUpdate(String callbackData) {
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(6789L);
        when(callbackQuery.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(12345L);

        return update;
    }
}