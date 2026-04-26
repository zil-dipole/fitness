package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.TrainingDay;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages ongoing program creation sessions for users
 */
@Component
public class ProgramCreationSessionManager {

    // Maps userId to their current program creation session
    private final Map<Long, ProgramCreationSession> sessions = new ConcurrentHashMap<>();

    public void startSession(Long userId, Program program) {
        sessions.put(userId, new ProgramCreationSession(program));
    }

    public ProgramCreationSession getSession(Long userId) {
        return sessions.get(userId);
    }

    public void endSession(Long userId) {
        sessions.remove(userId);
    }

    public boolean hasActiveSession(Long userId) {
        return sessions.containsKey(userId);
    }

    /**
     * Represents an ongoing program creation session
     */
    public static class ProgramCreationSession {
        private final Program program;
        private final List<TrainingDay> trainingDays = new CopyOnWriteArrayList<>();

        public ProgramCreationSession(Program program) {
            this.program = program;
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
                        return;
                    }
                }
            }

            trainingDays.add(trainingDay);
        }
        
        public List<TrainingDay> getTrainingDays() {
            return List.copyOf(trainingDays);
        }
        
        public int getTrainingDaysCount() {
            return trainingDays.size();
        }
    }
}
