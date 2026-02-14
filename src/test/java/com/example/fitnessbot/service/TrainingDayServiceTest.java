package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.parser.TrainingDayParser;
import com.example.fitnessbot.repository.ExerciseRepository;
import com.example.fitnessbot.repository.TrainingDayRepository;
import com.example.fitnessbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingDayServiceTest {

    private static final Long TEST_TELEGRAM_ID = 12345L;
    private static final Long TEST_CHAT_ID = 6789L;
    
    @Mock
    private TrainingDayParser parser;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private TrainingDayRepository trainingDayRepository;
    
    @Mock
    private ExerciseRepository exerciseRepository;
    
    private TrainingDayService trainingDayService;
    
    @BeforeEach
    void setUp() {
        trainingDayService = new TrainingDayService(parser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageWithExistingUser() {
        // Given
        String rawText = "Треня 1:\n\nРазминка:\n- Бег 5 мин\n";
        
        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        
        TrainingDay parsedTrainingDay = new TrainingDay();
        parsedTrainingDay.setTitle("");
        Exercise exercise = new Exercise();
        exercise.setName("Бег 5 мин");
        exercise.setSection("Разминка");
        parsedTrainingDay.setExercises(List.of(exercise));
        
        TrainingDay savedTrainingDay = new TrainingDay();
        savedTrainingDay.setId(1L);
        savedTrainingDay.setUser(user);
        savedTrainingDay.setRawText(rawText);
        savedTrainingDay.setTitle("Треня 1:");
        exercise.setTrainingDay(savedTrainingDay);
        savedTrainingDay.setExercises(List.of(exercise));
        
        when(userRepository.findByTelegramId(TEST_TELEGRAM_ID)).thenReturn(Optional.of(user));
        when(parser.parse(rawText)).thenReturn(parsedTrainingDay);
        when(trainingDayRepository.save(any(TrainingDay.class))).thenReturn(savedTrainingDay);
        
        // When
        TrainingDay result = trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, rawText);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getRawText()).isEqualTo(rawText);
        assertThat(result.getTitle()).isEqualTo("Треня 1:");
        assertThat(result.getExercises()).hasSize(1);
        assertThat(result.getExercises().get(0).getName()).isEqualTo("Бег 5 мин");
        assertThat(result.getExercises().get(0).getTrainingDay()).isEqualTo(result);
        
        verify(userRepository).findByTelegramId(TEST_TELEGRAM_ID);
        verify(parser).parse(rawText);
        verify(trainingDayRepository).save(any(TrainingDay.class));
        verifyNoMoreInteractions(exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageCreatesNewUser() {
        // Given
        String rawText = "Треня 1:\n\nРазминка:\n- Бег 5 мин\n";
        
        User newUser = new User();
        newUser.setTelegramId(TEST_TELEGRAM_ID);
        
        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setTelegramId(TEST_TELEGRAM_ID);
        
        TrainingDay parsedTrainingDay = new TrainingDay();
        parsedTrainingDay.setTitle("");
        Exercise exercise = new Exercise();
        exercise.setName("Бег 5 мин");
        exercise.setSection("Разминка");
        parsedTrainingDay.setExercises(List.of(exercise));
        
        TrainingDay savedTrainingDay = new TrainingDay();
        savedTrainingDay.setId(1L);
        savedTrainingDay.setUser(savedUser);
        savedTrainingDay.setRawText(rawText);
        savedTrainingDay.setTitle("Треня 1:");
        exercise.setTrainingDay(savedTrainingDay);
        savedTrainingDay.setExercises(List.of(exercise));
        
        when(userRepository.findByTelegramId(TEST_TELEGRAM_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(parser.parse(rawText)).thenReturn(parsedTrainingDay);
        when(trainingDayRepository.save(any(TrainingDay.class))).thenReturn(savedTrainingDay);
        
        // When
        TrainingDay result = trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, rawText);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUser().getId()).isEqualTo(1L);
        
        verify(userRepository).findByTelegramId(TEST_TELEGRAM_ID);
        verify(userRepository).save(any(User.class));
        verify(parser).parse(rawText);
        verify(trainingDayRepository).save(any(TrainingDay.class));
    }
    
    @Test
    void testProcessForwardedMessageNullUserId() {
        // Given
        String rawText = "Треня 1:\n\nРазминка:\n- Бег 5 мин\n";
        
        // When & Then
        assertThatThrownBy(() -> trainingDayService.processForwardedMessage(null, rawText))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Telegram user ID cannot be null");
        
        verifyNoInteractions(parser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageNullText() {
        // When & Then
        assertThatThrownBy(() -> trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Raw text cannot be null or empty");
        
        verifyNoInteractions(parser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageEmptyText() {
        // When & Then
        assertThatThrownBy(() -> trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Raw text cannot be null or empty");
        
        verifyNoInteractions(parser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageBlankText() {
        // When & Then
        assertThatThrownBy(() -> trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Raw text cannot be null or empty");
        
        verifyNoInteractions(parser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageTextTooLarge() {
        // Given
        String largeText = "A".repeat(10001); // More than 10KB limit
        
        // When & Then
        assertThatThrownBy(() -> trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, largeText))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Raw text is too large (max 10KB allowed)");
        
        verifyNoInteractions(parser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testGetTrainingDayByIdFound() {
        // Given
        Long trainingDayId = 1L;
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(trainingDayId);
        trainingDay.setTitle("Test Day");
        
        when(trainingDayRepository.findById(trainingDayId)).thenReturn(Optional.of(trainingDay));
        
        // When
        TrainingDay result = trainingDayService.getTrainingDayById(trainingDayId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(trainingDayId);
        assertThat(result.getTitle()).isEqualTo("Test Day");
        
        verify(trainingDayRepository).findById(trainingDayId);
    }
    
    @Test
    void testGetTrainingDayByIdNotFound() {
        // Given
        Long trainingDayId = 1L;
        when(trainingDayRepository.findById(trainingDayId)).thenReturn(Optional.empty());
        
        // When
        TrainingDay result = trainingDayService.getTrainingDayById(trainingDayId);
        
        // Then
        assertThat(result).isNull();
        
        verify(trainingDayRepository).findById(trainingDayId);
    }
    
    @Test
    void testProcessForwardedMessageWithNullExercises() {
        // Given
        String rawText = "Треня 1:";
        
        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        
        TrainingDay parsedTrainingDay = new TrainingDay();
        parsedTrainingDay.setTitle("");
        parsedTrainingDay.setExercises(null); // Null exercises
        
        TrainingDay savedTrainingDay = new TrainingDay();
        savedTrainingDay.setId(1L);
        savedTrainingDay.setUser(user);
        savedTrainingDay.setRawText(rawText);
        savedTrainingDay.setTitle("Треня 1:");
        savedTrainingDay.setExercises(null);
        
        when(userRepository.findByTelegramId(TEST_TELEGRAM_ID)).thenReturn(Optional.of(user));
        when(parser.parse(rawText)).thenReturn(parsedTrainingDay);
        when(trainingDayRepository.save(any(TrainingDay.class))).thenReturn(savedTrainingDay);
        
        // When
        TrainingDay result = trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, rawText);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExercises()).isNull();
        
        verify(userRepository).findByTelegramId(TEST_TELEGRAM_ID);
        verify(parser).parse(rawText);
        verify(trainingDayRepository).save(any(TrainingDay.class));
    }
    
    @Test
    void testProcessForwardedMessageSetsPositionForExercises() {
        // Given
        String rawText = "Треня 1:\n\nРазминка:\n- Бег 5 мин\n- Растяжка 10 мин\n";
        
        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        
        TrainingDay parsedTrainingDay = new TrainingDay();
        parsedTrainingDay.setTitle("");
        
        Exercise exercise1 = new Exercise();
        exercise1.setName("Бег 5 мин");
        exercise1.setSection("Разминка");
        
        Exercise exercise2 = new Exercise();
        exercise2.setName("Растяжка 10 мин");
        exercise2.setSection("Разминка");
        
        parsedTrainingDay.setExercises(List.of(exercise1, exercise2));
        
        TrainingDay savedTrainingDay = new TrainingDay();
        savedTrainingDay.setId(1L);
        savedTrainingDay.setUser(user);
        savedTrainingDay.setRawText(rawText);
        savedTrainingDay.setTitle("Треня 1:");
        
        exercise1.setTrainingDay(savedTrainingDay);
        exercise1.setPosition(0);
        exercise2.setTrainingDay(savedTrainingDay);
        exercise2.setPosition(1);
        
        savedTrainingDay.setExercises(List.of(exercise1, exercise2));
        
        when(userRepository.findByTelegramId(TEST_TELEGRAM_ID)).thenReturn(Optional.of(user));
        when(parser.parse(rawText)).thenReturn(parsedTrainingDay);
        when(trainingDayRepository.save(any(TrainingDay.class))).thenReturn(savedTrainingDay);
        
        // When
        TrainingDay result = trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, rawText);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getExercises()).hasSize(2);
        assertThat(result.getExercises().get(0).getPosition()).isEqualTo(0);
        assertThat(result.getExercises().get(1).getPosition()).isEqualTo(1);
        
        // Verify that exercises are properly associated with the training day
        ArgumentCaptor<TrainingDay> trainingDayCaptor = ArgumentCaptor.forClass(TrainingDay.class);
        verify(trainingDayRepository).save(trainingDayCaptor.capture());
        
        TrainingDay capturedTrainingDay = trainingDayCaptor.getValue();
        assertThat(capturedTrainingDay.getExercises()).hasSize(2);
        assertThat(capturedTrainingDay.getExercises().get(0).getTrainingDay()).isEqualTo(capturedTrainingDay);
        assertThat(capturedTrainingDay.getExercises().get(1).getTrainingDay()).isEqualTo(capturedTrainingDay);
        
        verify(userRepository).findByTelegramId(TEST_TELEGRAM_ID);
        verify(parser).parse(rawText);
    }
}