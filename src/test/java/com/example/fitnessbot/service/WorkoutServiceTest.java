package com.example.fitnessbot.service;

import com.example.fitnessbot.exception.WorkoutException;
import com.example.fitnessbot.model.*;
import com.example.fitnessbot.repository.ExerciseRepository;
import com.example.fitnessbot.repository.TrainingDayRepository;
import com.example.fitnessbot.repository.UserRepository;
import com.example.fitnessbot.repository.WorkoutSessionRepository;
import com.example.fitnessbot.repository.WorkoutSetLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @Mock
    private TrainingDayRepository trainingDayRepository;

    private WorkoutService workoutService;

    @BeforeEach
    void setUp() {
        workoutService = new WorkoutService(userRepository, workoutSessionRepository, workoutSetLogRepository, exerciseRepository, trainingDayRepository);
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
        assertThat(view.circuit()).isFalse();
        assertThat(view.repsOrDuration()).isEqualTo("8");
        assertThat(view.videoUrls()).containsExactly("https://video.example/bench");
        assertThat(view.previousWeightKg()).isNull();
    }

    @Test
    void startActiveTrainingDayLoadsExerciseVideosFromRepository() throws Exception {
        TrainingDay activeTrainingDay = trainingDayWithExercises();
        activeTrainingDay.getExercises().getFirst().setVideoUrls(List.of());
        TrainingDay loadedTrainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(activeTrainingDay);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(trainingDayRepository.findByIdWithExercises(10L)).thenReturn(Optional.of(loadedTrainingDay));
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

        assertThat(view.videoUrls()).containsExactly("https://video.example/bench");
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
        assertThat(result.exerciseView().circuit()).isFalse();
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
    void recordWeightStartsWorkoutSessionFromActiveTrainingDayWhenNoneExists() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(workoutSessionRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> {
            WorkoutSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(100L);
            }
            return session;
        });
        when(workoutSetLogRepository.findHistoryLogsForExerciseIdentity(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        WorkoutService.WeightEntryResult result = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "60");

        assertThat(result.accepted()).isTrue();
        assertThat(result.dayCompleted()).isFalse();
        assertThat(result.message()).contains("Saved set 1: 60 kg");
        assertThat(result.exerciseView().sessionId()).isEqualTo(100L);
        assertThat(result.exerciseView().currentSetNumber()).isEqualTo(2);
        assertThat(result.exerciseView().previousWeightKg()).isEqualTo(60.0);
        assertThat(result.exerciseView().previousLoad()).isEqualTo("60 kg");

        ArgumentCaptor<WorkoutSetLog> logCaptor = ArgumentCaptor.forClass(WorkoutSetLog.class);
        verify(workoutSetLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getWorkoutSession().getId()).isEqualTo(100L);
        assertThat(logCaptor.getValue().getExercise().getName()).isEqualTo("Bench Press");
        assertThat(logCaptor.getValue().getWeightKg()).isEqualTo(60.0);
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

        WorkoutService.WeightEntryResult result = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "red band");

        assertThat(result.accepted()).isTrue();
        assertThat(result.message()).contains("Saved set 1: red band");
        assertThat(result.exerciseView().currentSetNumber()).isEqualTo(2);
        assertThat(result.exerciseView().previousLoad()).isEqualTo("red band");

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
    void recordLoadAdvancesCircuitExercisesByRounds() throws Exception {
        TrainingDay trainingDay = trainingDayWithCircuit();
        User user = userWithActiveTrainingDay(trainingDay);
        WorkoutSession session = activeSession(user, trainingDay, trainingDay.getExercises().get(1));
        List<WorkoutSetLog> currentSessionLogs = new ArrayList<>();

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));
        doAnswer(invocation -> {
            WorkoutSetLog savedLog = invocation.getArgument(0);
            currentSessionLogs.add(savedLog);
            return savedLog;
        }).when(workoutSetLogRepository).save(any(WorkoutSetLog.class));
        when(workoutSetLogRepository.findByWorkoutSessionIdAndExerciseIdOrderBySetNumberAsc(eq(100L), anyLong()))
                .thenAnswer(invocation -> {
                    Long exerciseId = invocation.getArgument(1);
                    return currentSessionLogs.stream()
                            .filter(log -> exerciseId.equals(log.getExercise().getId()))
                            .toList();
                });
        when(workoutSetLogRepository.findHistoryLogsForExerciseIdentity(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        WorkoutService.WeightEntryResult first = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none");
        assertCurrentStep(first, session, "B", 1, true, 3);
        assertThat(first.message()).contains("Saved round 1: no load. Next exercise:");

        assertCurrentStep(workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none"), session, "C", 1, true, 3);
        WorkoutService.WeightEntryResult secondRound = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none");
        assertCurrentStep(secondRound, session, "A", 2, true, 3);
        assertThat(secondRound.message()).contains("Saved round 1: no load. Next round:");

        assertCurrentStep(workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none"), session, "B", 2, true, 3);
        assertCurrentStep(workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none"), session, "C", 2, true, 3);
        assertCurrentStep(workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none"), session, "A", 3, true, 3);
        assertCurrentStep(workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none"), session, "B", 3, true, 3);
        assertCurrentStep(workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none"), session, "C", 3, true, 3);

        WorkoutService.WeightEntryResult afterCircuit = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none");
        assertCurrentStep(afterCircuit, session, "After Circuit", 1, false, 1);
        assertThat(afterCircuit.message()).contains("Saved round 3: no load. Next exercise:");

        WorkoutService.WeightEntryResult completed = workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none");

        assertThat(completed.dayCompleted()).isTrue();
        assertThat(completed.message()).contains("Training day completed");
        assertThat(currentSessionLogs).extracting(log -> log.getExercise().getName())
                .containsExactly("A", "B", "C", "A", "B", "C", "A", "B", "C", "After Circuit");
        assertThat(currentSessionLogs).extracting(WorkoutSetLog::getSetNumber)
                .containsExactly(1, 1, 1, 2, 2, 2, 3, 3, 3, 1);
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
        verify(workoutSetLogRepository, atLeastOnce()).findHistoryLogsForExerciseIdentity(eq(1L), eq(99L), eq("bench press"), eq(100L), any());
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
        assertThat(result.exerciseView().previousLoad()).isEqualTo("72.5 kg");

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
    void recordPreviousWeightUsesCustomCurrentSessionLoad() throws Exception {
        TrainingDay trainingDay = trainingDayWithExercises();
        User user = userWithActiveTrainingDay(trainingDay);
        Exercise exercise = trainingDay.getExercises().getFirst();
        WorkoutSession session = activeSession(user, trainingDay, exercise);
        session.setCurrentSetNumber(2);
        List<WorkoutSetLog> currentSessionLogs = new ArrayList<>();
        WorkoutSetLog firstSetLog = new WorkoutSetLog();
        firstSetLog.setWorkoutSession(session);
        firstSetLog.setUser(user);
        firstSetLog.setTrainingDay(trainingDay);
        firstSetLog.setExercise(exercise);
        firstSetLog.setSetNumber(1);
        firstSetLog.setLoadDescription("orange band");
        firstSetLog.setCreatedAt(LocalDateTime.now());
        currentSessionLogs.add(firstSetLog);

        when(userRepository.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(Optional.of(user));
        when(workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(1L, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));
        when(workoutSetLogRepository.findByWorkoutSessionIdAndExerciseIdOrderBySetNumberAsc(100L, 11L))
                .thenAnswer(invocation -> List.copyOf(currentSessionLogs));
        doAnswer(invocation -> {
            WorkoutSetLog savedLog = invocation.getArgument(0);
            currentSessionLogs.add(savedLog);
            return savedLog;
        }).when(workoutSetLogRepository).save(any(WorkoutSetLog.class));
        when(workoutSetLogRepository.findHistoryLogsForExerciseIdentity(anyLong(), anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of());

        WorkoutService.WeightEntryResult result = workoutService.recordPreviousWeightForCurrentSet(TELEGRAM_USER_ID);

        assertThat(result.accepted()).isTrue();
        assertThat(result.message()).contains("Saved set 2: orange band");
        assertThat(result.exerciseView().currentSetNumber()).isEqualTo(3);
        assertThat(result.exerciseView().previousWeightKg()).isNull();
        assertThat(result.exerciseView().previousLoad()).isEqualTo("orange band");

        ArgumentCaptor<WorkoutSetLog> logCaptor = ArgumentCaptor.forClass(WorkoutSetLog.class);
        verify(workoutSetLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getSetNumber()).isEqualTo(2);
        assertThat(logCaptor.getValue().getWeightKg()).isNull();
        assertThat(logCaptor.getValue().getLoadDescription()).isEqualTo("orange band");
        verify(exerciseRepository, never()).save(any());
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
        assertThat(result.message()).isEqualTo("No previous load is available for this exercise.");
        assertThat(result.exerciseView()).isNull();
        verify(workoutSetLogRepository).findByWorkoutSessionIdAndExerciseIdOrderBySetNumberAsc(100L, 11L);
        verify(workoutSetLogRepository).findHistoryLogsForExerciseIdentity(eq(1L), eq(11L), eq("bench press"), eq(100L), any());
        verifyNoInteractions(exerciseRepository);
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

    private void assertCurrentStep(WorkoutService.WeightEntryResult result,
                                   WorkoutSession session,
                                   String exerciseName,
                                   int currentSetNumber,
                                   boolean circuit,
                                   int totalSets) {
        assertThat(result.accepted()).isTrue();
        assertThat(result.dayCompleted()).isFalse();
        assertThat(result.exerciseView().exerciseName()).isEqualTo(exerciseName);
        assertThat(result.exerciseView().currentSetNumber()).isEqualTo(currentSetNumber);
        assertThat(result.exerciseView().circuit()).isEqualTo(circuit);
        assertThat(result.exerciseView().totalSets()).isEqualTo(totalSets);
        assertThat(session.getCurrentExercise().getName()).isEqualTo(exerciseName);
        assertThat(session.getCurrentSetNumber()).isEqualTo(currentSetNumber);
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

    private TrainingDay trainingDayWithCircuit() {
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(20L);
        trainingDay.setTitle("Circuit Day");

        Exercise warmup = exercise(trainingDay, 21L, 1, "Warmup", "warmup", "Prep", 1, "5");
        Exercise first = exercise(trainingDay, 22L, 2, "A", "a", "ISO 3 круга", 1, "10");
        Exercise second = exercise(trainingDay, 23L, 3, "B", "b", "ISO 3 круга", 1, "12");
        Exercise third = exercise(trainingDay, 24L, 4, "C", "c", "ISO 3 круга", 1, "15");
        Exercise after = exercise(trainingDay, 25L, 5, "After Circuit", "after circuit", "Main", 1, "8");

        trainingDay.setExercises(List.of(warmup, first, second, third, after));
        return trainingDay;
    }

    private Exercise exercise(TrainingDay trainingDay,
                              Long id,
                              int position,
                              String name,
                              String normalizedName,
                              String section,
                              int sets,
                              String repsOrDuration) {
        Exercise exercise = new Exercise();
        exercise.setId(id);
        exercise.setTrainingDay(trainingDay);
        exercise.setPosition(position);
        exercise.setName(name);
        exercise.setNormalizedName(normalizedName);
        exercise.setSection(section);
        exercise.setSets(sets);
        exercise.setRepsOrDuration(repsOrDuration);
        return exercise;
    }
}
