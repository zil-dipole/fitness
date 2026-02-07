package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.TrainingDay;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        private final Map<Long, TrainingDay> trainingDays = new HashMap<>();
        
        public ProgramCreationSession(Program program) {
            this.program = program;
        }
        
        public Program getProgram() {
            return program;
        }
        
        public void addTrainingDay(TrainingDay trainingDay) {
            trainingDays.put(trainingDay.getId(), trainingDay);
        }
        
        public List<TrainingDay> getTrainingDays() {
            return trainingDays.values().stream().toList();
        }
        
        public int getTrainingDaysCount() {
            return trainingDays.size();
        }
    }
}