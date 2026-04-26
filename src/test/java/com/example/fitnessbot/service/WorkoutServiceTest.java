package com.example.fitnessbot.service;

import com.example.fitnessbot.exception.WorkoutException;
import com.example.fitnessbot.model.*;
import com.example.fitnessbot.repository.ExerciseRepository;
import com.example.fitnessbot.repository.UserRepository;
import com.example.fitnessbot.repository.WorkoutSessionRepository;
import com.example.fitnessbot.repository.WorkoutSetLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    private static final long TELEGRAM_USER_ID = 12345L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;

    @Mock
    private WorkoutSetLogRepository workoutSetLogRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    private WorkoutService workoutService;

    @BeforeEach
    void setUp() {
        workoutService = new WorkoutService(userRepository, workoutSessionRepository, workoutSetLogRepository, exerciseRepository);
    }

    @Test
    void startActiveTrainingDayCreatesSessionAtFirstExercise() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> {
            WorkoutSession session = invocation.getArgument(0);
            session.setId(100L);
            return session;
        });
        when(workoutSetLogRepository.findHistoryLogsForExerciseIdentity(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        WorkoutService.WorkoutExerciseView view = workoutService.startActiveTrainingDay(TELEGRAM_USER_ID);

        assertThat(view.sessionId()).isEqualTo(100L);
        assertThat(view.trainingDayTitle()).isEqualTo("Upper Body");
        assertThat(view.exerciseName()).isEqualTo("Bench Press");
        assertThat(view.exerciseNumber()).isEqualTo(1);
        assertThat(view.totalExercises()).isEqualTo(2);
        assertThat(view.currentSetNumber()).isEqualTo(1);
        assertThat(view.totalSets()).isEqualTo(3);
        assertThat(view.repsOrDuration()).isEqualTo("8");
        assertThat(view.videoUrls()).containsExactly("https://video.example/bench");
        assertThat(view.previousWeightKg()).isNull();
    }

    @Test
    void recordWeightSavesCurrentSetAndAdvancesToNextSet() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);
        Exercise exercise = trainingDay.getExercises().getFirst();
        WorkoutSession session = activeSession(user, trainingDay, exercise);
        List<WorkoutSetLog> currentSessionLogs = new ArrayList<>();

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));
        doAnswer(invocation -> {
            WorkoutSetLog savedLog = invocation.getArgument(0);
            currentSessionLogs.add(savedLog);
            return savedLog;
        }).when(workoutSetLogRepository).save(any(WorkoutSetLog.class));
        when(workoutSetLogRepository.findByWorkoutSessionIdAndExerciseIdOrderBySetNumberAsc(100L, 11L))
                .thenAnswer(invocation -> List.copyOf(currentSessionLogs));
        when(workoutSetLogRepository.findHistoryLogsForExerciseIdentity(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        WorkoutService.WeightEntryResult result = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "60,5");

        assertThat(result.accepted()).isTrue();
        assertThat(result.dayCompleted()).isFalse();
        assertThat(result.message()).contains("Saved set 1: 60.5 kg");
        assertThat(result.exerciseView().currentSetNumber()).isEqualTo(2);
        assertThat(result.exerciseView().history()).hasSize(1);
        assertThat(result.exerciseView().history().getFirst().loads()).containsExactly("60.5 kg");
        assertThat(session.getCurrentSetNumber()).isEqualTo(2);

        ArgumentCaptor<WorkoutSetLog> logCaptor = ArgumentCaptor.forClass(WorkoutSetLog.class);
        verify(workoutSetLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getSetNumber()).isEqualTo(1);
        assertThat(logCaptor.getValue().getWeightKg()).isEqualTo(60.5);
        assertThat(logCaptor.getValue().getLoadDescription()).isNull();
        assertThat(exercise.getLastWeightKg()).isEqualTo(60.5);
        verify(exerciseRepository).save(exercise);
    }

    @Test
    void recordWeightCompletesDayAfterFinalSetOfFinalExercise() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);
        Exercise finalExercise = trainingDay.getExercises().get(1);
        WorkoutSession session = activeSession(user, trainingDay, finalExercise);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));

        WorkoutService.WeightEntryResult result = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "42");

        assertThat(result.accepted()).isTrue();
        assertThat(result.dayCompleted()).isTrue();
        assertThat(result.message()).contains("Training day completed");
        assertThat(session.getStatus()).isEqualTo(WorkoutSessionStatus.COMPLETED);
        assertThat(session.getCompletedAt()).isNotNull();
    }

    @Test
    void recordTextLoadSavesDescriptionAndAdvancesToNextSet() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);
        Exercise exercise = trainingDay.getExercises().getFirst();
        WorkoutSession session = activeSession(user, trainingDay, exercise);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));
        when(workoutSetLogRepository.findHistoryLogsForExerciseIdentity(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        WorkoutService.WeightEntryResult result = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "red band");

        assertThat(result.accepted()).isTrue();
        assertThat(result.message()).contains("Saved set 1: red band");
        assertThat(result.exerciseView().currentSetNumber()).isEqualTo(2);

        ArgumentCaptor<WorkoutSetLog> logCaptor = ArgumentCaptor.forClass(WorkoutSetLog.class);
        verify(workoutSetLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getWeightKg()).isNull();
        assertThat(logCaptor.getValue().getLoadDescription()).isEqualTo("red band");
        assertThat(exercise.getLastWeightKg()).isNull();
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void recordNoLoadSavesEmptyLoadAndAdvancesToNextSet() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);
        Exercise exercise = trainingDay.getExercises().getFirst();
        WorkoutSession session = activeSession(user, trainingDay, exercise);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));
        when(workoutSetLogRepository.findHistoryLogsForExerciseIdentity(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        WorkoutService.WeightEntryResult result = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none");

        assertThat(result.accepted()).isTrue();
        assertThat(result.message()).contains("Saved set 1: no load");
        assertThat(result.exerciseView().currentSetNumber()).isEqualTo(2);

        ArgumentCaptor<WorkoutSetLog> logCaptor = ArgumentCaptor.forClass(WorkoutSetLog.class);
        verify(workoutSetLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getWeightKg()).isNull();
        assertThat(logCaptor.getValue().getLoadDescription()).isNull();
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void recordWeightRejectsBlankInput() throws WorkoutException {
        WorkoutService.WeightEntryResult result = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "   ");

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("Send load for this set");
        verifyNoInteractions(userRepository, workoutSessionRepository, workoutSetLogRepository, exerciseRepository);
    }

    @Test
    void recordWeightUpdatesCanonicalExerciseLastWeight() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);
        Exercise exercise = trainingDay.getExercises().getFirst();
        Exercise canonicalExercise = new Exercise();
        canonicalExercise.setId(99L);
        canonicalExercise.setName("Bench Press");
        canonicalExercise.setNormalizedName("bench press");
        exercise.setCanonicalExercise(canonicalExercise);
        WorkoutSession session = activeSession(user, trainingDay, exercise);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));
        when(workoutSetLogRepository.findHistoryLogsForExerciseIdentity(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        WorkoutService.WeightEntryResult result = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "70");

        assertThat(result.accepted()).isTrue();
        assertThat(exercise.getLastWeightKg()).isEqualTo(70.0);
        assertThat(canonicalExercise.getLastWeightKg()).isEqualTo(70.0);
        verify(exerciseRepository).save(exercise);
        verify(exerciseRepository).save(canonicalExercise);
        verify(workoutSetLogRepository).findHistoryLogsForExerciseIdentity(eq(1L), eq(99L), eq("bench press"), eq(100L), any());
    }

    @Test
    void recordPreviousWeightUsesCurrentExerciseLastWeight() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);
        Exercise exercise = trainingDay.getExercises().getFirst();
        exercise.setLastWeightKg(72.5);
        WorkoutSession session = activeSession(user, trainingDay, exercise);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));
        when(workoutSetLogRepository.findHistoryLogsForExerciseIdentity(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        WorkoutService.WeightEntryResult result = workoutService.recordPreviousWeightForCurrentSet(TELEGRAM_USER_ID);

        assertThat(result.accepted()).isTrue();
        assertThat(result.message()).contains("Saved set 1: 72.5 kg");
        assertThat(result.exerciseView().previousWeightKg()).isEqualTo(72.5);

        ArgumentCaptor<WorkoutSetLog> logCaptor = ArgumentCaptor.forClass(WorkoutSetLog.class);
        verify(workoutSetLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getWeightKg()).isEqualTo(72.5);
        assertThat(logCaptor.getValue().getLoadDescription()).isNull();
    }

    @Test
    void recordPreviousWeightUsesCanonicalExerciseLastWeight() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);
        Exercise exercise = trainingDay.getExercises().getFirst();
        Exercise canonicalExercise = new Exercise();
        canonicalExercise.setId(99L);
        canonicalExercise.setName("Bench Press");
        canonicalExercise.setNormalizedName("bench press");
        canonicalExercise.setLastWeightKg(80.0);
        exercise.setCanonicalExercise(canonicalExercise);
        WorkoutSession session = activeSession(user, trainingDay, exercise);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));
        when(workoutSetLogRepository.findHistoryLogsForExerciseIdentity(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        WorkoutService.WeightEntryResult result = workoutService.recordPreviousWeightForCurrentSet(TELEGRAM_USER_ID);

        assertThat(result.accepted()).isTrue();
        assertThat(result.message()).contains("Saved set 1: 80 kg");
        assertThat(exercise.getLastWeightKg()).isEqualTo(80.0);
        assertThat(canonicalExercise.getLastWeightKg()).isEqualTo(80.0);

        ArgumentCaptor<WorkoutSetLog> logCaptor = ArgumentCaptor.forClass(WorkoutSetLog.class);
        verify(workoutSetLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getWeightKg()).isEqualTo(80.0);
        verify(exerciseRepository).save(exercise);
        verify(exerciseRepository).save(canonicalExercise);
    }

    @Test
    void recordPreviousWeightRejectsWhenUnavailable() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);
        Exercise exercise = trainingDay.getExercises().getFirst();
        WorkoutSession session = activeSession(user, trainingDay, exercise);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));

        WorkoutService.WeightEntryResult result = workoutService.recordPreviousWeightForCurrentSet(TELEGRAM_USER_ID);

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).isEqualTo("No previous weight is available for this exercise.");
        assertThat(result.exerciseView()).isNull();
        verifyNoInteractions(workoutSetLogRepository, exerciseRepository);
    }

    private User userWithActiveTrainingDay(TrainingDay trainingDay) {
        User user = new User();
        user.setId(1L);
        user.setTelegramId(TELEGRAM_USER_ID);
        user.setActiveTrainingDay(trainingDay);
        trainingDay.setUser(user);
        return user;
    }

    private WorkoutSession activeSession(User user, TrainingDay trainingDay, Exercise exercise) {
        WorkoutSession session = new WorkoutSession();
        session.setId(100L);
        session.setUser(user);
        session.setTrainingDay(trainingDay);
        session.setCurrentExercise(exercise);
        session.setCurrentSetNumber(1);
        session.setStatus(WorkoutSessionStatus.IN_PROGRESS);
        return session;
    }

    private TrainingDay trainingDayWithExercises() {
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(10L);
        trainingDay.setTitle("Upper Body");

        Exercise bench = new Exercise();
        bench.setId(11L);
        bench.setTrainingDay(trainingDay);
        bench.setPosition(1);
        bench.setName("Bench Press");
        bench.setNormalizedName("bench press");
        bench.setSets(3);
        bench.setRepsOrDuration("8");
        bench.setNotes("Warm up first");
        bench.setVideoUrls(List.of("https://video.example/bench"));

        Exercise row = new Exercise();
        row.setId(12L);
        row.setTrainingDay(trainingDay);
        row.setPosition(2);
        row.setName("Row");
        row.setNormalizedName("row");
        row.setSets(1);
        row.setRepsOrDuration("10");

        trainingDay.setExercises(List.of(bench, row));
        return trainingDay;
    }
}
