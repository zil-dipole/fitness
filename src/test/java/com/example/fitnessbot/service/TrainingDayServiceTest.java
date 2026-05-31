package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.parser.OpenAiTrainingDayParser;
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
    private OpenAiTrainingDayParser openAiTrainingDayParser;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private TrainingDayRepository trainingDayRepository;
    
    @Mock
    private ExerciseRepository exerciseRepository;
    
    private TrainingDayService trainingDayService;
    
    @BeforeEach
    void setUp() {
        trainingDayService = new TrainingDayService(parser, openAiTrainingDayParser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageWithExistingUser() {
        // Given
        String rawText = "Треня 1:\n\nРазминка:\n- Бег 5 мин\n";
        
        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        user.setUseAiParser(false);
        
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
        verifyNoInteractions(openAiTrainingDayParser);
        verify(exerciseRepository).findCanonicalExercisesForUser(eq(1L), eq("бег 5 мин"), any());
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
        savedUser.setUseAiParser(false);
        
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
        verifyNoInteractions(openAiTrainingDayParser);
    }
    
    @Test
    void testProcessForwardedMessageNullUserId() {
        // Given
        String rawText = "Треня 1:\n\nРазминка:\n- Бег 5 мин\n";
        
        // When & Then
        assertThatThrownBy(() -> trainingDayService.processForwardedMessage(null, rawText))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Telegram user ID cannot be null");
        
        verifyNoInteractions(parser, openAiTrainingDayParser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageNullText() {
        // When & Then
        assertThatThrownBy(() -> trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Raw text cannot be null or empty");
        
        verifyNoInteractions(parser, openAiTrainingDayParser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageEmptyText() {
        // When & Then
        assertThatThrownBy(() -> trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Raw text cannot be null or empty");
        
        verifyNoInteractions(parser, openAiTrainingDayParser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageBlankText() {
        // When & Then
        assertThatThrownBy(() -> trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Raw text cannot be null or empty");
        
        verifyNoInteractions(parser, openAiTrainingDayParser, userRepository, trainingDayRepository, exerciseRepository);
    }
    
    @Test
    void testProcessForwardedMessageTextTooLarge() {
        // Given
        String largeText = "A".repeat(10001); // More than 10KB limit
        
        // When & Then
        assertThatThrownBy(() -> trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, largeText))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Raw text is too large (max 10KB allowed)");
        
        verifyNoInteractions(parser, openAiTrainingDayParser, userRepository, trainingDayRepository, exerciseRepository);
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
        user.setUseAiParser(false);
        
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
        verifyNoInteractions(openAiTrainingDayParser);
    }
    
    @Test
    void testProcessForwardedMessageSetsPositionForExercises() {
        // Given
        String rawText = "Треня 1:\n\nРазминка:\n- Бег 5 мин\n- Растяжка 10 мин\n";
        
        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        user.setUseAiParser(false);
        
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
        
        Exercise savedExercise1 = new Exercise();
        savedExercise1.setName("Бег 5 мин");
        savedExercise1.setSection("Разминка");
        savedExercise1.setTrainingDay(savedTrainingDay);
        savedExercise1.setPosition(0);

        Exercise savedExercise2 = new Exercise();
        savedExercise2.setName("Растяжка 10 мин");
        savedExercise2.setSection("Разминка");
        savedExercise2.setTrainingDay(savedTrainingDay);
        savedExercise2.setPosition(1);
        
        savedTrainingDay.setExercises(List.of(savedExercise1, savedExercise2));
        
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
        verifyNoInteractions(openAiTrainingDayParser);
    }

    @Test
    void testProcessForwardedMessageUsesOpenAiParserForFlaggedUser() {
        String rawText = "Workout A\n1. Squat 3 x 5 100 kg";

        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        user.setUseAiParser(true);

        TrainingDay parsedTrainingDay = new TrainingDay();
        parsedTrainingDay.setTitle("Workout A");
        Exercise exercise = new Exercise();
        exercise.setPosition(0);
        exercise.setSection("General");
        exercise.setName("Squat");
        exercise.setSets(3);
        exercise.setRepsOrDuration("5");
        exercise.setNotes("100 kg");
        exercise.setLastWeightKg(100.0);
        parsedTrainingDay.setExercises(List.of(exercise));

        TrainingDay savedTrainingDay = new TrainingDay();
        savedTrainingDay.setId(1L);
        savedTrainingDay.setUser(user);
        savedTrainingDay.setRawText(rawText);
        savedTrainingDay.setTitle("Workout A");
        exercise.setTrainingDay(savedTrainingDay);
        savedTrainingDay.setExercises(List.of(exercise));

        when(userRepository.findByTelegramId(TEST_TELEGRAM_ID)).thenReturn(Optional.of(user));
        when(openAiTrainingDayParser.parse(rawText)).thenReturn(parsedTrainingDay);
        when(trainingDayRepository.save(any(TrainingDay.class))).thenReturn(savedTrainingDay);

        TrainingDay result = trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, rawText);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Workout A");
        verify(userRepository).findByTelegramId(TEST_TELEGRAM_ID);
        verify(openAiTrainingDayParser).parse(rawText);
        verify(trainingDayRepository).save(any(TrainingDay.class));
        verifyNoInteractions(parser);
    }

    @Test
    void testProcessForwardedMessageUsesOpenAiSpecificInputForFlaggedUser() {
        String rawText = "Upper Body:\n- Bench press 3 x 8";
        String aiRawText = """
                Sheet: Upper Body
                Spreadsheet rows; cells are separated by " | ":
                Exercise | Sets | Reps
                Bench press | 3 | 8
                """;

        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        user.setUseAiParser(true);

        TrainingDay parsedTrainingDay = new TrainingDay();
        parsedTrainingDay.setTitle("Upper Body");
        Exercise exercise = new Exercise();
        exercise.setPosition(0);
        exercise.setSection("General");
        exercise.setName("Bench press");
        exercise.setSets(3);
        exercise.setRepsOrDuration("8");
        parsedTrainingDay.setExercises(List.of(exercise));

        when(userRepository.findByTelegramId(TEST_TELEGRAM_ID)).thenReturn(Optional.of(user));
        when(openAiTrainingDayParser.parse(aiRawText)).thenReturn(parsedTrainingDay);
        when(trainingDayRepository.save(any(TrainingDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrainingDay result = trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, rawText, aiRawText);

        assertThat(result.getRawText()).isEqualTo(rawText);
        assertThat(result.getTitle()).isEqualTo("Upper Body");
        verify(openAiTrainingDayParser).parse(aiRawText);
        verifyNoInteractions(parser);
    }

    @Test
    void testProcessForwardedMessageNormalizesMistypedRussianTitle() {
        String rawText = "Nhtyz 2:\n\nРазминка:\n- Бег 5 мин\n";

        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        user.setUseAiParser(false);

        TrainingDay parsedTrainingDay = new TrainingDay();
        parsedTrainingDay.setTitle("");
        Exercise exercise = new Exercise();
        exercise.setName("Бег 5 мин");
        exercise.setSection("Разминка");
        parsedTrainingDay.setExercises(List.of(exercise));

        when(userRepository.findByTelegramId(TEST_TELEGRAM_ID)).thenReturn(Optional.of(user));
        when(parser.parse(rawText)).thenReturn(parsedTrainingDay);
        when(trainingDayRepository.save(any(TrainingDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrainingDay result = trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, rawText);

        assertThat(result.getTitle()).isEqualTo("Треня 2:");
    }

    @Test
    void testProcessForwardedMessageLinksRepeatedExerciseToCanonicalExercise() {
        String rawText = "Треня 2:\n\nMain:\n- Bench Press 3 x 8\n";

        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        user.setUseAiParser(false);

        Exercise canonicalExercise = new Exercise();
        canonicalExercise.setId(99L);
        canonicalExercise.setName("Bench Press");
        canonicalExercise.setNormalizedName("bench press");
        canonicalExercise.setLastWeightKg(75.0);

        TrainingDay parsedTrainingDay = new TrainingDay();
        parsedTrainingDay.setTitle("Треня 2:");
        Exercise exercise = new Exercise();
        exercise.setName("Bench Press");
        exercise.setSection("Main");
        exercise.setSets(3);
        exercise.setRepsOrDuration("8");
        parsedTrainingDay.setExercises(List.of(exercise));

        when(userRepository.findByTelegramId(TEST_TELEGRAM_ID)).thenReturn(Optional.of(user));
        when(parser.parse(rawText)).thenReturn(parsedTrainingDay);
        when(exerciseRepository.findCanonicalExercisesForUser(eq(1L), eq("bench press"), any()))
                .thenReturn(List.of(canonicalExercise));
        when(trainingDayRepository.save(any(TrainingDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrainingDay result = trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, rawText);

        Exercise savedExercise = result.getExercises().getFirst();
        assertThat(savedExercise.getNormalizedName()).isEqualTo("bench press");
        assertThat(savedExercise.getCanonicalExercise()).isEqualTo(canonicalExercise);
        assertThat(savedExercise.getLastWeightKg()).isEqualTo(75.0);
    }

    @Test
    void testProcessForwardedMessageTruncatesLongNormalizedExerciseName() {
        String rawText = "Workout:\n- Long exercise\n";
        String longExerciseName = "Bench " + "Press ".repeat(60);

        User user = new User();
        user.setId(1L);
        user.setTelegramId(TEST_TELEGRAM_ID);
        user.setUseAiParser(false);

        TrainingDay parsedTrainingDay = new TrainingDay();
        parsedTrainingDay.setTitle("Workout:");
        Exercise exercise = new Exercise();
        exercise.setName(longExerciseName);
        exercise.setSection("Main");
        parsedTrainingDay.setExercises(List.of(exercise));

        when(userRepository.findByTelegramId(TEST_TELEGRAM_ID)).thenReturn(Optional.of(user));
        when(parser.parse(rawText)).thenReturn(parsedTrainingDay);
        when(exerciseRepository.findCanonicalExercisesForUser(eq(1L), anyString(), any()))
                .thenReturn(List.of());
        when(trainingDayRepository.save(any(TrainingDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrainingDay result = trainingDayService.processForwardedMessage(TEST_TELEGRAM_ID, rawText);

        ArgumentCaptor<String> normalizedNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(exerciseRepository).findCanonicalExercisesForUser(eq(1L), normalizedNameCaptor.capture(), any());
        assertThat(normalizedNameCaptor.getValue()).hasSize(255);
        assertThat(result.getExercises().getFirst().getNormalizedName()).hasSize(255);
    }
}
