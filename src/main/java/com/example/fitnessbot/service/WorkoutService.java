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
    private static final Pattern CIRCUIT_SECTION_PATTERN = Pattern.compile(".*?(\\d+)\\s*(?:rounds?|circles?|круг(?:а|ов)?).*", Pattern.CASE_INSENSITIVE);
    private static final Set<String> NO_LOAD_INPUTS = Set.of("-", "none", "no weight", "no load", "skip");

    public record WorkoutExerciseView(
            Long sessionId,
            String trainingDayTitle,
            String exerciseName,
            int exerciseNumber,
            int totalExercises,
            int currentSetNumber,
            int totalSets,
            boolean circuit,
            String repsOrDuration,
            String notes,
            List<String> videoUrls,
            Double previousWeightKg,
            String previousLoad,
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

    private record CircuitGroup(int startIndex, int endIndex, int rounds) {
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

    @Transactional(readOnly = true)
    public boolean hasWorkoutInputContext(Long telegramUserId) {
        Optional<User> user = userRepository.findByTelegramId(telegramUserId);
        if (user.isEmpty()) {
            return false;
        }

        boolean hasActiveSession = workoutSessionRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(
                user.get().getId(),
                WorkoutSessionStatus.IN_PROGRESS
        ).isPresent();
        return hasActiveSession || user.get().getActiveTrainingDay() != null;
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
        WorkoutSession session = findActiveOrStartWorkoutSession(user);

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

        updateLastWeight(exercise, load.weightKg());
        return advanceAfterRecordedSet(session, exercise, savedSetNumber, load.displayValue());
    }

    @Transactional
    public WeightEntryResult recordPreviousWeightForCurrentSet(Long telegramUserId) throws WorkoutException {
        User user = userRepository.findByTelegramId(telegramUserId)
                .orElseThrow(() -> new WorkoutException("You don't have an active workout session."));
        WorkoutSession session = workoutSessionRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(user.getId(), WorkoutSessionStatus.IN_PROGRESS)
                .orElseThrow(() -> new WorkoutException("You don't have an active workout session."));

        Exercise exercise = session.getCurrentExercise();
        if (exercise == null) {
            throw new WorkoutException("Current workout exercise is missing.");
        }

        String previousLoad = previousLoadFor(session, exercise);
        if (previousLoad == null) {
            return new WeightEntryResult(
                    false,
                    false,
                    "No previous load is available for this exercise.",
                    null
            );
        }

        return recordWeightForCurrentSet(telegramUserId, previousLoad);
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

        return advanceAfterSkippedStep(session, currentExercise);
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

    private WorkoutSession findActiveOrStartWorkoutSession(User user) throws WorkoutException {
        Optional<WorkoutSession> activeSession = workoutSessionRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(user.getId(), WorkoutSessionStatus.IN_PROGRESS);
        if (activeSession.isPresent()) {
            return activeSession.get();
        }

        TrainingDay activeTrainingDay = user.getActiveTrainingDay();
        if (activeTrainingDay == null) {
            throw new WorkoutException("You don't have an active workout session.");
        }

        List<Exercise> exercises = orderedExercises(activeTrainingDay);
        if (exercises.isEmpty()) {
            throw new WorkoutException("Active training day has no exercises.");
        }

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setTrainingDay(activeTrainingDay);
        session.setCurrentExercise(exercises.getFirst());
        session.setCurrentSetNumber(1);
        session.setStatus(WorkoutSessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());
        return workoutSessionRepository.save(session);
    }

    private WeightEntryResult advanceAfterRecordedSet(WorkoutSession session,
                                                      Exercise exercise,
                                                      int savedSetNumber,
                                                      String displayValue) {
        List<Exercise> exercises = orderedExercises(session.getTrainingDay());
        Optional<CircuitGroup> circuitGroup = circuitGroup(exercises, exercise);
        if (circuitGroup.isPresent()) {
            return advanceCircuit(
                    session,
                    exercises,
                    exercise,
                    circuitGroup.get(),
                    "Saved round " + savedSetNumber + ": " + displayValue + ". Next round:",
                    "Saved round " + savedSetNumber + ": " + displayValue + ". Next exercise:",
                    "Saved round " + savedSetNumber + ": " + displayValue + ". Training day completed."
            );
        }

        int totalSets = totalSets(exercise);
        if (savedSetNumber < totalSets) {
            session.setCurrentSetNumber(savedSetNumber + 1);
            workoutSessionRepository.save(session);
            return new WeightEntryResult(
                    true,
                    false,
                    "Saved set " + savedSetNumber + ": " + displayValue + ".",
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
                    "Saved set " + savedSetNumber + ": " + displayValue + ". Next exercise:",
                    toView(session)
            );
        }

        completeSession(session);
        return new WeightEntryResult(true, true, "Saved set " + savedSetNumber + ": " + displayValue + ". Training day completed.", null);
    }

    private WeightEntryResult advanceAfterSkippedStep(WorkoutSession session, Exercise currentExercise) {
        List<Exercise> exercises = orderedExercises(session.getTrainingDay());
        Optional<CircuitGroup> circuitGroup = circuitGroup(exercises, currentExercise);
        if (circuitGroup.isPresent()) {
            return advanceCircuit(
                    session,
                    exercises,
                    currentExercise,
                    circuitGroup.get(),
                    "Skipped exercise. Next round:",
                    "Skipped exercise. Next exercise:",
                    "Skipped final exercise. Training day completed."
            );
        }

        Optional<Exercise> nextExercise = nextExercise(exercises, currentExercise);
        if (nextExercise.isPresent()) {
            session.setCurrentExercise(nextExercise.get());
            session.setCurrentSetNumber(1);
            workoutSessionRepository.save(session);
            return new WeightEntryResult(true, false, "Skipped exercise. Next exercise:", toView(session));
        }

        completeSession(session);
        return new WeightEntryResult(true, true, "Skipped final exercise. Training day completed.", null);
    }

    private WeightEntryResult advanceCircuit(WorkoutSession session,
                                             List<Exercise> exercises,
                                             Exercise currentExercise,
                                             CircuitGroup group,
                                             String nextRoundMessage,
                                             String nextExerciseMessage,
                                             String completedMessage) {
        int currentIndex = exerciseIndex(exercises, currentExercise);
        int currentRound = session.getCurrentSetNumber() == null ? 1 : session.getCurrentSetNumber();

        if (currentIndex + 1 < group.endIndex()) {
            session.setCurrentExercise(exercises.get(currentIndex + 1));
            workoutSessionRepository.save(session);
            return new WeightEntryResult(true, false, nextExerciseMessage, toView(session));
        }

        if (currentRound < group.rounds()) {
            session.setCurrentExercise(exercises.get(group.startIndex()));
            session.setCurrentSetNumber(currentRound + 1);
            workoutSessionRepository.save(session);
            return new WeightEntryResult(true, false, nextRoundMessage, toView(session));
        }

        if (group.endIndex() < exercises.size()) {
            session.setCurrentExercise(exercises.get(group.endIndex()));
            session.setCurrentSetNumber(1);
            workoutSessionRepository.save(session);
            return new WeightEntryResult(true, false, nextExerciseMessage, toView(session));
        }

        completeSession(session);
        return new WeightEntryResult(true, true, completedMessage, null);
    }

    private void completeSession(WorkoutSession session) {
        session.setStatus(WorkoutSessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        workoutSessionRepository.save(session);
    }

    private WorkoutExerciseView toView(WorkoutSession session) {
        TrainingDay trainingDay = session.getTrainingDay();
        List<Exercise> exercises = orderedExercises(trainingDay);
        Exercise exercise = session.getCurrentExercise();
        int exerciseIndex = exerciseIndex(exercises, exercise);
        Optional<CircuitGroup> circuitGroup = circuitGroup(exercises, exercise);
        int totalSets = circuitGroup.map(CircuitGroup::rounds).orElseGet(() -> totalSets(exercise));

        return new WorkoutExerciseView(
                session.getId(),
                trainingDay.getTitle(),
                exercise.getName(),
                exerciseIndex + 1,
                exercises.size(),
                session.getCurrentSetNumber(),
                totalSets,
                circuitGroup.isPresent(),
                exercise.getRepsOrDuration(),
                exercise.getNotes(),
                exercise.getVideoUrls() == null ? List.of() : List.copyOf(exercise.getVideoUrls()),
                previousWeightFor(exercise),
                previousLoadFor(session, exercise),
                historyFor(session, exercise)
        );
    }

    private List<WorkoutHistoryEntry> historyFor(WorkoutSession session, Exercise exercise) {
        List<WorkoutHistoryEntry> history = new ArrayList<>();

        List<WorkoutSetLog> currentSessionLogs = Optional.ofNullable(workoutSetLogRepository
                        .findByWorkoutSessionIdAndExerciseIdOrderBySetNumberAsc(session.getId(), exercise.getId()))
                .orElseGet(List::of);
        if (!currentSessionLogs.isEmpty()) {
            List<String> currentLoads = currentSessionLogs.stream()
                    .map(this::formatLoad)
                    .toList();
            history.add(new WorkoutHistoryEntry(session.getStartedAt(), currentLoads));
        }

        List<WorkoutSetLog> logs = Optional.ofNullable(workoutSetLogRepository
                        .findHistoryLogsForExerciseIdentity(
                                session.getUser().getId(),
                                canonicalExerciseId(exercise),
                                exercise.getNormalizedName(),
                                session.getId(),
                                PageRequest.of(0, HISTORY_LOG_LIMIT)
                        ))
                .orElseGet(List::of);

        Map<Long, List<WorkoutSetLog>> logsBySession = new LinkedHashMap<>();
        for (WorkoutSetLog log : logs) {
            logsBySession.computeIfAbsent(log.getWorkoutSession().getId(), ignored -> new ArrayList<>()).add(log);
        }

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

    private Optional<CircuitGroup> circuitGroup(List<Exercise> exercises, Exercise exercise) {
        int currentIndex = exerciseIndex(exercises, exercise);
        if (currentIndex < 0) {
            return Optional.empty();
        }

        Optional<Integer> rounds = circuitRounds(exercise);
        if (rounds.isEmpty()) {
            return Optional.empty();
        }

        String section = exercise.getSection();
        int startIndex = currentIndex;
        while (startIndex > 0 && sameSection(section, exercises.get(startIndex - 1).getSection())) {
            startIndex--;
        }

        int endIndex = currentIndex + 1;
        while (endIndex < exercises.size() && sameSection(section, exercises.get(endIndex).getSection())) {
            endIndex++;
        }

        return Optional.of(new CircuitGroup(startIndex, endIndex, rounds.get()));
    }

    private Optional<Integer> circuitRounds(Exercise exercise) {
        if (exercise == null || exercise.getSection() == null) {
            return Optional.empty();
        }

        Matcher matcher = CIRCUIT_SECTION_PATTERN.matcher(exercise.getSection());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        int rounds = Integer.parseInt(matcher.group(1));
        if (rounds < 2) {
            return Optional.empty();
        }
        return Optional.of(rounds);
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

    private String previousLoadFor(WorkoutSession session, Exercise exercise) {
        Optional<String> currentSessionLoad = reusableCurrentSessionLoad(session, exercise);
        if (currentSessionLoad.isPresent()) {
            return currentSessionLoad.get();
        }

        Optional<String> historyLoad = reusableHistoryLoad(session, exercise);
        if (historyLoad.isPresent()) {
            return historyLoad.get();
        }

        Double previousWeight = previousWeightFor(exercise);
        if (previousWeight != null) {
            return formatWeight(previousWeight) + " kg";
        }
        return null;
    }

    private Optional<String> reusableCurrentSessionLoad(WorkoutSession session, Exercise exercise) {
        List<WorkoutSetLog> currentSessionLogs = Optional.ofNullable(workoutSetLogRepository
                        .findByWorkoutSessionIdAndExerciseIdOrderBySetNumberAsc(session.getId(), exercise.getId()))
                .orElseGet(List::of);
        Integer currentSetNumber = session.getCurrentSetNumber();
        return currentSessionLogs.stream()
                .filter(log -> currentSetNumber == null
                        || log.getSetNumber() == null
                        || log.getSetNumber() < currentSetNumber)
                .sorted(Comparator.comparing(
                        WorkoutSetLog::getSetNumber,
                        Comparator.nullsLast(Integer::compareTo)
                ).reversed())
                .map(this::reusableLoad)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<String> reusableHistoryLoad(WorkoutSession session, Exercise exercise) {
        List<WorkoutSetLog> logs = Optional.ofNullable(workoutSetLogRepository
                        .findHistoryLogsForExerciseIdentity(
                                session.getUser().getId(),
                                canonicalExerciseId(exercise),
                                exercise.getNormalizedName(),
                                session.getId(),
                                PageRequest.of(0, HISTORY_LOG_LIMIT)
                        ))
                .orElseGet(List::of);
        return logs.stream()
                .map(this::reusableLoad)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<String> reusableLoad(WorkoutSetLog setLog) {
        if (setLog.getWeightKg() != null && setLog.getWeightKg() > 0) {
            return Optional.of(formatWeight(setLog.getWeightKg()) + " kg");
        }
        if (setLog.getLoadDescription() != null && !setLog.getLoadDescription().isBlank()) {
            return Optional.of(setLog.getLoadDescription().trim());
        }
        return Optional.empty();
    }

    private void updateLastWeight(Exercise exercise, Double weightKg) {
        if (weightKg == null) {
            return;
        }

        exercise.setLastWeightKg(weightKg);
        exerciseRepository.save(exercise);

        Exercise canonicalExercise = exercise.getCanonicalExercise();
        if (canonicalExercise != null && !sameId(canonicalExercise.getId(), exercise.getId())) {
            canonicalExercise.setLastWeightKg(weightKg);
            exerciseRepository.save(canonicalExercise);
        }
    }

    private Double previousWeightFor(Exercise exercise) {
        if (exercise == null) {
            return null;
        }
        if (exercise.getLastWeightKg() != null && exercise.getLastWeightKg() > 0) {
            return exercise.getLastWeightKg();
        }

        Exercise canonicalExercise = exercise.getCanonicalExercise();
        if (canonicalExercise != null
                && canonicalExercise.getLastWeightKg() != null
                && canonicalExercise.getLastWeightKg() > 0) {
            return canonicalExercise.getLastWeightKg();
        }
        return null;
    }

    private Long canonicalExerciseId(Exercise exercise) {
        Exercise canonicalExercise = exercise.getCanonicalExercise();
        if (canonicalExercise != null && canonicalExercise.getId() != null) {
            return canonicalExercise.getId();
        }
        return exercise.getId();
    }

    private boolean sameId(Long first, Long second) {
        return first != null && first.equals(second);
    }

    private boolean sameSection(String first, String second) {
        return Objects.equals(first, second);
    }

    private String formatWeight(double weight) {
        if (weight == (long) weight) {
            return Long.toString((long) weight);
        }
        return Double.toString(weight);
    }
}
