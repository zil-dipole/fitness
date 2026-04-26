package com.example.fitnessbot.service;

import com.example.fitnessbot.exception.WorkoutException;
import com.example.fitnessbot.model.*;
import com.example.fitnessbot.repository.ExerciseRepository;
import com.example.fitnessbot.repository.UserRepository;
import com.example.fitnessbot.repository.WorkoutSessionRepository;
import com.example.fitnessbot.repository.WorkoutSetLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WorkoutService {

    private static final int HISTORY_LOG_LIMIT = 30;
    private static final int HISTORY_SESSION_LIMIT = 3;
    private static final Pattern WEIGHT_INPUT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)(?:\\s*(?:kg|kgs|kilogram|kilograms))?", Pattern.CASE_INSENSITIVE);
    private static final Set<String> NO_LOAD_INPUTS = Set.of("-", "none", "no weight", "no load", "skip");

    public record WorkoutExerciseView(
            Long sessionId,
            String trainingDayTitle,
            String exerciseName,
            int exerciseNumber,
            int totalExercises,
            int currentSetNumber,
            int totalSets,
            String repsOrDuration,
            String notes,
            List<String> videoUrls,
            List<WorkoutHistoryEntry> history
    ) {
    }

    public record WorkoutHistoryEntry(LocalDateTime startedAt, List<String> loads) {
    }

    public record WeightEntryResult(
            boolean accepted,
            boolean dayCompleted,
            String message,
            WorkoutExerciseView exerciseView
    ) {
    }

    private record SetLoad(Double weightKg, String description, String displayValue) {
    }

    private final UserRepository userRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutSetLogRepository workoutSetLogRepository;
    private final ExerciseRepository exerciseRepository;

    public WorkoutService(UserRepository userRepository,
                          WorkoutSessionRepository workoutSessionRepository,
                          WorkoutSetLogRepository workoutSetLogRepository,
                          ExerciseRepository exerciseRepository) {
        this.userRepository = userRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutSetLogRepository = workoutSetLogRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveWorkoutSession(Long telegramUserId) {
        return userRepository.findByTelegramId(telegramUserId)
                .flatMap(user -> workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(
                        user.getId(),
                        WorkoutSessionStatus.IN_PROGRESS
                ))
                .isPresent();
    }

    @Transactional
    public WorkoutExerciseView startActiveTrainingDay(Long telegramUserId) throws WorkoutException {
        User user = userRepository.findByTelegramId(telegramUserId)
                .orElseThrow(() -> new WorkoutException("You don't have an active training day."));

        TrainingDay activeTrainingDay = user.getActiveTrainingDay();
        if (activeTrainingDay == null) {
            throw new WorkoutException("You don't have an active training day.");
        }

        List<Exercise> exercises = orderedExercises(activeTrainingDay);
        if (exercises.isEmpty()) {
            throw new WorkoutException("Active training day has no exercises.");
        }

        Optional<WorkoutSession> activeSession = workoutSessionRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(user.getId(), WorkoutSessionStatus.IN_PROGRESS);
        if (activeSession.isPresent()) {
            WorkoutSession session = activeSession.get();
            if (sameId(activeTrainingDay.getId(), session.getTrainingDay().getId())) {
                ensureCurrentExercise(session, exercises.getFirst());
                return toView(session);
            }

            session.setStatus(WorkoutSessionStatus.ABANDONED);
            session.setCompletedAt(LocalDateTime.now());
            workoutSessionRepository.save(session);
        }

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setTrainingDay(activeTrainingDay);
        session.setCurrentExercise(exercises.getFirst());
        session.setCurrentSetNumber(1);
        session.setStatus(WorkoutSessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());

        return toView(workoutSessionRepository.save(session));
    }

    @Transactional
    public WeightEntryResult recordWeightForCurrentSet(Long telegramUserId, String input) throws WorkoutException {
        Optional<SetLoad> parsedLoad = parseSetLoad(input);
        if (parsedLoad.isEmpty()) {
            return new WeightEntryResult(
                    false,
                    false,
                    "Send load for this set, for example: 60, red band, or bodyweight. Send none for no load.",
                    null
            );
        }

        User user = userRepository.findByTelegramId(telegramUserId)
                .orElseThrow(() -> new WorkoutException("You don't have an active workout session."));
        WorkoutSession session = workoutSessionRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(user.getId(), WorkoutSessionStatus.IN_PROGRESS)
                .orElseThrow(() -> new WorkoutException("You don't have an active workout session."));

        Exercise exercise = session.getCurrentExercise();
        if (exercise == null) {
            throw new WorkoutException("Current workout exercise is missing.");
        }

        int savedSetNumber = session.getCurrentSetNumber();
        SetLoad load = parsedLoad.get();

        WorkoutSetLog setLog = new WorkoutSetLog();
        setLog.setWorkoutSession(session);
        setLog.setUser(user);
        setLog.setTrainingDay(session.getTrainingDay());
        setLog.setExercise(exercise);
        setLog.setSetNumber(savedSetNumber);
        setLog.setWeightKg(load.weightKg());
        setLog.setLoadDescription(load.description());
        setLog.setCreatedAt(LocalDateTime.now());
        workoutSetLogRepository.save(setLog);

        if (load.weightKg() != null) {
            exercise.setLastWeightKg(load.weightKg());
            exerciseRepository.save(exercise);
        }

        List<Exercise> exercises = orderedExercises(session.getTrainingDay());
        int totalSets = totalSets(exercise);
        if (savedSetNumber < totalSets) {
            session.setCurrentSetNumber(savedSetNumber + 1);
            workoutSessionRepository.save(session);
            return new WeightEntryResult(
                    true,
                    false,
                    "Saved set " + savedSetNumber + ": " + load.displayValue() + ".",
                    toView(session)
            );
        }

        Optional<Exercise> nextExercise = nextExercise(exercises, exercise);
        if (nextExercise.isPresent()) {
            session.setCurrentExercise(nextExercise.get());
            session.setCurrentSetNumber(1);
            workoutSessionRepository.save(session);
            return new WeightEntryResult(
                    true,
                    false,
                    "Saved set " + savedSetNumber + ": " + load.displayValue() + ". Next exercise:",
                    toView(session)
            );
        }

        session.setStatus(WorkoutSessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        workoutSessionRepository.save(session);
        return new WeightEntryResult(true, true, "Saved set " + savedSetNumber + ": " + load.displayValue() + ". Training day completed.", null);
    }

    @Transactional
    public WeightEntryResult skipCurrentExercise(Long telegramUserId) throws WorkoutException {
        User user = userRepository.findByTelegramId(telegramUserId)
                .orElseThrow(() -> new WorkoutException("You don't have an active workout session."));
        WorkoutSession session = workoutSessionRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(user.getId(), WorkoutSessionStatus.IN_PROGRESS)
                .orElseThrow(() -> new WorkoutException("You don't have an active workout session."));

        Exercise currentExercise = session.getCurrentExercise();
        if (currentExercise == null) {
            throw new WorkoutException("Current workout exercise is missing.");
        }

        Optional<Exercise> nextExercise = nextExercise(orderedExercises(session.getTrainingDay()), currentExercise);
        if (nextExercise.isPresent()) {
            session.setCurrentExercise(nextExercise.get());
            session.setCurrentSetNumber(1);
            workoutSessionRepository.save(session);
            return new WeightEntryResult(true, false, "Skipped exercise. Next exercise:", toView(session));
        }

        session.setStatus(WorkoutSessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        workoutSessionRepository.save(session);
        return new WeightEntryResult(true, true, "Skipped final exercise. Training day completed.", null);
    }

    @Transactional
    public boolean finishActiveWorkout(Long telegramUserId) {
        Optional<User> user = userRepository.findByTelegramId(telegramUserId);
        if (user.isEmpty()) {
            return false;
        }

        Optional<WorkoutSession> session = workoutSessionRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(user.get().getId(), WorkoutSessionStatus.IN_PROGRESS);
        if (session.isEmpty()) {
            return false;
        }

        WorkoutSession activeSession = session.get();
        activeSession.setStatus(WorkoutSessionStatus.COMPLETED);
        activeSession.setCompletedAt(LocalDateTime.now());
        workoutSessionRepository.save(activeSession);
        return true;
    }

    private void ensureCurrentExercise(WorkoutSession session, Exercise firstExercise) {
        if (session.getCurrentExercise() == null) {
            session.setCurrentExercise(firstExercise);
        }
        if (session.getCurrentSetNumber() == null || session.getCurrentSetNumber() < 1) {
            session.setCurrentSetNumber(1);
        }
    }

    private WorkoutExerciseView toView(WorkoutSession session) {
        TrainingDay trainingDay = session.getTrainingDay();
        List<Exercise> exercises = orderedExercises(trainingDay);
        Exercise exercise = session.getCurrentExercise();
        int exerciseIndex = exerciseIndex(exercises, exercise);
        int totalSets = totalSets(exercise);

        return new WorkoutExerciseView(
                session.getId(),
                trainingDay.getTitle(),
                exercise.getName(),
                exerciseIndex + 1,
                exercises.size(),
                session.getCurrentSetNumber(),
                totalSets,
                exercise.getRepsOrDuration(),
                exercise.getNotes(),
                exercise.getVideoUrls() == null ? List.of() : List.copyOf(exercise.getVideoUrls()),
                historyFor(session, exercise)
        );
    }

    private List<WorkoutHistoryEntry> historyFor(WorkoutSession session, Exercise exercise) {
        List<WorkoutSetLog> logs = workoutSetLogRepository
                .findByUserIdAndExerciseIdAndWorkoutSessionIdNotOrderByCreatedAtDesc(
                        session.getUser().getId(),
                        exercise.getId(),
                        session.getId(),
                        PageRequest.of(0, HISTORY_LOG_LIMIT)
                );

        Map<Long, List<WorkoutSetLog>> logsBySession = new LinkedHashMap<>();
        for (WorkoutSetLog log : logs) {
            logsBySession.computeIfAbsent(log.getWorkoutSession().getId(), ignored -> new ArrayList<>()).add(log);
        }

        List<WorkoutHistoryEntry> history = new ArrayList<>();
        for (List<WorkoutSetLog> sessionLogs : logsBySession.values()) {
            if (history.size() == HISTORY_SESSION_LIMIT) {
                break;
            }

            sessionLogs.sort(Comparator.comparing(WorkoutSetLog::getSetNumber));
            List<String> loads = sessionLogs.stream()
                    .map(this::formatLoad)
                    .toList();
            history.add(new WorkoutHistoryEntry(sessionLogs.getFirst().getWorkoutSession().getStartedAt(), loads));
        }
        return history;
    }

    private List<Exercise> orderedExercises(TrainingDay trainingDay) {
        if (trainingDay.getExercises() == null) {
            return List.of();
        }

        return trainingDay.getExercises().stream()
                .sorted(Comparator
                        .comparing(Exercise::getPosition, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Exercise::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private Optional<Exercise> nextExercise(List<Exercise> exercises, Exercise currentExercise) {
        int index = exerciseIndex(exercises, currentExercise);
        if (index < 0 || index + 1 >= exercises.size()) {
            return Optional.empty();
        }
        return Optional.of(exercises.get(index + 1));
    }

    private int exerciseIndex(List<Exercise> exercises, Exercise exercise) {
        if (exercise == null) {
            return -1;
        }
        for (int i = 0; i < exercises.size(); i++) {
            if (sameId(exercises.get(i).getId(), exercise.getId())) {
                return i;
            }
        }
        return -1;
    }

    private int totalSets(Exercise exercise) {
        if (exercise == null || exercise.getSets() == null || exercise.getSets() < 1) {
            return 1;
        }
        return exercise.getSets();
    }

    private Optional<SetLoad> parseSetLoad(String input) {
        if (input == null) {
            return Optional.empty();
        }

        String normalized = input.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        if (NO_LOAD_INPUTS.contains(normalized.toLowerCase(Locale.ROOT))) {
            return Optional.of(new SetLoad(null, null, "no load"));
        }

        Matcher matcher = WEIGHT_INPUT_PATTERN.matcher(normalized.replace(',', '.'));
        if (matcher.matches()) {
            double weight = Double.parseDouble(matcher.group(1));
            if (weight < 0) {
                return Optional.empty();
            }
            if (weight == 0) {
                return Optional.of(new SetLoad(null, null, "no load"));
            }
            return Optional.of(new SetLoad(weight, null, formatWeight(weight) + " kg"));
        }

        return Optional.of(new SetLoad(null, normalized, normalized));
    }

    private String formatLoad(WorkoutSetLog setLog) {
        if (setLog.getWeightKg() != null) {
            return formatWeight(setLog.getWeightKg()) + " kg";
        }
        if (setLog.getLoadDescription() != null && !setLog.getLoadDescription().isBlank()) {
            return setLog.getLoadDescription();
        }
        return "no load";
    }

    private boolean sameId(Long first, Long second) {
        return first != null && first.equals(second);
    }

    private String formatWeight(double weight) {
        if (weight == (long) weight) {
            return Long.toString((long) weight);
        }
        return Double.toString(weight);
    }
}
