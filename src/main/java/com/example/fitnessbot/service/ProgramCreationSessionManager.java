package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.TrainingDay;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages ongoing program creation sessions for users
 */
@Component
public class ProgramCreationSessionManager {

    private static final String SESSION_KEY_PREFIX = "fitness:program-creation:session:";
    private static final String AWAITING_NAME_KEY_PREFIX = "fitness:program-creation:awaiting-name:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProgramCreationSessionManager(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void startSession(Long userId, Program program) {
        redisTemplate.delete(awaitingNameKey(userId));
        saveSession(userId, new ProgramCreationSession(program, List.of(), session -> saveSession(userId, session)));
    }

    public void startAwaitingProgramName(Long userId) {
        redisTemplate.opsForValue().set(awaitingNameKey(userId), "true");
    }

    public ProgramCreationSession getSession(Long userId) {
        String sessionJson = redisTemplate.opsForValue().get(sessionKey(userId));
        if (sessionJson == null) {
            return null;
        }
        ProgramCreationSession session = readSession(sessionJson);
        session.onChange(sessionToSave -> saveSession(userId, sessionToSave));
        return session;
    }

    public void endSession(Long userId) {
        redisTemplate.delete(List.of(sessionKey(userId), awaitingNameKey(userId)));
    }

    public boolean hasActiveSession(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(userId)));
    }

    public boolean isAwaitingProgramName(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(awaitingNameKey(userId)));
    }

    public boolean hasProgramCreationInProgress(Long userId) {
        return hasActiveSession(userId) || isAwaitingProgramName(userId);
    }

    private String sessionKey(Long userId) {
        return SESSION_KEY_PREFIX + userId;
    }

    private String awaitingNameKey(Long userId) {
        return AWAITING_NAME_KEY_PREFIX + userId;
    }

    private void saveSession(Long userId, ProgramCreationSession session) {
        try {
            redisTemplate.opsForValue().set(sessionKey(userId), objectMapper.writeValueAsString(SessionSnapshot.from(session)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize program creation session for user " + userId, e);
        }
    }

    private ProgramCreationSession readSession(String sessionJson) {
        try {
            return objectMapper.readValue(sessionJson, SessionSnapshot.class).toSession();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize program creation session", e);
        }
    }

    /**
     * Represents an ongoing program creation session
     */
    public static class ProgramCreationSession {
        private final Program program;
        private final List<TrainingDay> trainingDays = new CopyOnWriteArrayList<>();
        private Consumer<ProgramCreationSession> onChange;

        public ProgramCreationSession(Program program) {
            this.program = program;
        }

        private ProgramCreationSession(Program program,
                                       List<TrainingDay> trainingDays,
                                       Consumer<ProgramCreationSession> onChange) {
            this.program = program;
            this.trainingDays.addAll(trainingDays);
            this.onChange = onChange;
        }

        private void onChange(Consumer<ProgramCreationSession> onChange) {
            this.onChange = onChange;
        }
        
        public Program getProgram() {
            return program;
        }
        
        public void addTrainingDay(TrainingDay trainingDay) {
            Long trainingDayId = trainingDay.getId();
            if (trainingDayId != null) {
                for (int i = 0; i < trainingDays.size(); i++) {
                    TrainingDay existingTrainingDay = trainingDays.get(i);
                    if (trainingDayId.equals(existingTrainingDay.getId())) {
                        trainingDays.set(i, trainingDay);
                        if (onChange != null) {
                            onChange.accept(this);
                        }
                        return;
                    }
                }
            }

            trainingDays.add(trainingDay);
            if (onChange != null) {
                onChange.accept(this);
            }
        }
        
        public List<TrainingDay> getTrainingDays() {
            return List.copyOf(trainingDays);
        }
        
        public int getTrainingDaysCount() {
            return trainingDays.size();
        }
    }

    private record SessionSnapshot(ProgramSnapshot program, List<TrainingDaySnapshot> trainingDays) {
        private static SessionSnapshot from(ProgramCreationSession session) {
            return new SessionSnapshot(
                    ProgramSnapshot.from(session.getProgram()),
                    session.getTrainingDays().stream()
                            .map(TrainingDaySnapshot::from)
                            .toList()
            );
        }

        private ProgramCreationSession toSession() {
            return new ProgramCreationSession(program.toProgram(), trainingDays.stream()
                    .map(TrainingDaySnapshot::toTrainingDay)
                    .toList(), null);
        }
    }

    private record ProgramSnapshot(Long id, String name) {
        private static ProgramSnapshot from(Program program) {
            return new ProgramSnapshot(program.getId(), program.getName());
        }

        private Program toProgram() {
            Program program = new Program();
            program.setId(id);
            program.setName(name);
            return program;
        }
    }

    private record TrainingDaySnapshot(Long id, String title) {
        private static TrainingDaySnapshot from(TrainingDay trainingDay) {
            return new TrainingDaySnapshot(trainingDay.getId(), trainingDay.getTitle());
        }

        private TrainingDay toTrainingDay() {
            TrainingDay trainingDay = new TrainingDay();
            trainingDay.setId(id);
            trainingDay.setTitle(title);
            return trainingDay;
        }
    }
}
