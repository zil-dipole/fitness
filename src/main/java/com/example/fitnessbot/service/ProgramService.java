package com.example.fitnessbot.service;

import com.example.fitnessbot.model.*;
import com.example.fitnessbot.repository.ProgramRepository;
import com.example.fitnessbot.repository.ProgramTrainingDayRepository;
import com.example.fitnessbot.repository.TrainingDayRepository;
import com.example.fitnessbot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final ProgramTrainingDayRepository programTrainingDayRepository;
    private final TrainingDayRepository trainingDayRepository;
    private final UserRepository userRepository;
    private final ProgramCreationSessionManager sessionManager;

    public ProgramService(ProgramRepository programRepository,
                          ProgramTrainingDayRepository programTrainingDayRepository,
                          TrainingDayRepository trainingDayRepository,
                          UserRepository userRepository,
                          ProgramCreationSessionManager sessionManager) {
        this.programRepository = programRepository;
        this.programTrainingDayRepository = programTrainingDayRepository;
        this.trainingDayRepository = trainingDayRepository;
        this.userRepository = userRepository;
        this.sessionManager = sessionManager;
    }

    /**
     * Start creating a new program for a user
     * @param telegramUserId Telegram user ID
     * @param programName Name of the program
     * @return The created program
     */
    public Program startProgramCreation(Long telegramUserId, String programName) {
        User user = userRepository.findByTelegramId(telegramUserId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramId(telegramUserId);
                    return userRepository.save(newUser);
                });

        Program program = new Program();
        program.setUser(user);
        program.setName(programName);

        return programRepository.save(program);
    }

    /**
     * Add a training day to an existing program
     * @param programId ID of the program
     * @param trainingDayId ID of the training day to add
     * @param position Position in the program
     * @return The ProgramTrainingDay entity that links them
     */
    public ProgramTrainingDay addTrainingDayToProgram(Long programId, Long trainingDayId, Integer position) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Program not found with ID: " + programId));

        TrainingDay trainingDay = trainingDayRepository.findById(trainingDayId)
                .orElseThrow(() -> new RuntimeException("Training day not found with ID: " + trainingDayId));

        // Check that the training day belongs to the same user as the program
        if (!program.getUser().getId().equals(trainingDay.getUser().getId())) {
            throw new RuntimeException("Training day does not belong to the same user as the program");
        }

        ProgramTrainingDay programTrainingDay = new ProgramTrainingDay();
        programTrainingDay.setProgram(program);
        programTrainingDay.setTrainingDay(trainingDay);
        programTrainingDay.setPosition(position);

        return programTrainingDayRepository.save(programTrainingDay);
    }

    /**
     * Get all programs for a user
     * @param telegramUserId Telegram user ID
     * @return List of programs
     */
    public List<Program> getProgramsForUser(Long telegramUserId) {
        return programRepository.findByUserId(telegramUserId);
    }

    /**
     * Get a specific program for a user
     * @param programId ID of the program
     * @param telegramUserId Telegram user ID
     * @return The program if found
     */
    public Optional<Program> getProgramForUser(Long programId, Long telegramUserId) {
        return programRepository.findByIdAndUserId(programId, telegramUserId);
    }

    /**
     * Check if user has an active program creation session
     * @param telegramUserId Telegram user ID
     * @return true if user has an active session
     */
    public boolean hasActiveSession(Long telegramUserId) {
        return sessionManager.hasActiveSession(telegramUserId);
    }

    /**
     * Get the current program in creation for a user
     * @param telegramUserId Telegram user ID
     * @return The program in creation, or null if no active session
     */
    public Program getCurrentProgramInCreation(Long telegramUserId) {
        if (hasActiveSession(telegramUserId)) {
            return sessionManager.getSession(telegramUserId).getProgram();
        }
        return null;
    }
    
    /**
     * Get the most recently created program for a user (considered as active program)
     * @param telegramUserId Telegram user ID
     * @return The most recent program, or null if none exists
     */
    public Program getActiveProgram(Long telegramUserId) {
        Optional<User> optionalUser = userRepository.findByTelegramId(telegramUserId);
        if (optionalUser.isEmpty()) {
            return null;
        }
        
        User user = optionalUser.get();
        return programRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
    }
}