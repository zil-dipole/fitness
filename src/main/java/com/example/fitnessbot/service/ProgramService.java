package com.example.fitnessbot.service;

import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.exception.TrainingDayException;
import com.example.fitnessbot.model.*;
import com.example.fitnessbot.repository.ProgramRepository;
import com.example.fitnessbot.repository.ProgramTrainingDayRepository;
import com.example.fitnessbot.repository.TrainingDayRepository;
import com.example.fitnessbot.repository.UserRepository;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProgramService {

    public record ActiveProgramSelection(Program program, TrainingDay trainingDay, int weekNumber) {
    }

    public record ActiveTrainingDayProgression(
            TrainingDay trainingDay,
            int weekNumber,
            boolean wrappedToFirstDay,
            boolean completedFiveWeeks
    ) {
    }

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
     * @throws ProgramException if there's an error creating the program
     */
    public Program startProgramCreation(Long telegramUserId, String programName) throws ProgramException {
        try {
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
        } catch (Exception e) {
            throw new ProgramException("Error creating program: " + e.getMessage(), e);
        }
    }

    /**
     * Add a training day to an existing program
     * @param programId ID of the program
     * @param trainingDayId ID of the training day to add
     * @param position Position in the program
     * @return The ProgramTrainingDay entity that links them
     * @throws ProgramException if program is not found or training day doesn't belong to same user
     * @throws TrainingDayException if training day is not found
     */
    public ProgramTrainingDay addTrainingDayToProgram(Long programId, Long trainingDayId, Integer position) 
            throws ProgramException, TrainingDayException {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ProgramException("Program not found with ID: " + programId));

        TrainingDay trainingDay = trainingDayRepository.findById(trainingDayId)
                .orElseThrow(() -> new TrainingDayException("Training day not found with ID: " + trainingDayId));

        // Check that the training day belongs to the same user as the program
        if (!program.getUser().getId().equals(trainingDay.getUser().getId())) {
            throw new ProgramException("Training day does not belong to the same user as the program");
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
        return userRepository.findByTelegramId(telegramUserId)
                .map(user -> programRepository.findByUserId(user.getId()))
                .orElseGet(List::of);
    }

    /**
     * Get a specific program for a user
     * @param programId ID of the program
     * @param telegramUserId Telegram user ID
     * @return The program if found
     */
    public Optional<Program> getProgramForUser(Long programId, Long telegramUserId) {
        return userRepository.findByTelegramId(telegramUserId)
                .flatMap(user -> programRepository.findByIdAndUserId(programId, user.getId()));
    }

    /**
     * Get ordered training days linked to a program if it belongs to the Telegram user.
     * @param programId ID of the program
     * @param telegramUserId Telegram user ID
     * @return ordered program-training day links, or an empty list if the program is not owned by the user
     */
    public List<ProgramTrainingDay> getProgramTrainingDaysForUser(Long programId, Long telegramUserId) {
        return getProgramForUser(programId, telegramUserId)
                .map(program -> programTrainingDayRepository.findByProgramIdOrderByPositionAsc(program.getId()))
                .orElseGet(List::of);
    }

    @Transactional
    public ActiveProgramSelection startProgramForUser(Long programId, Long telegramUserId) throws ProgramException {
        User user = userRepository.findByTelegramId(telegramUserId)
                .orElseThrow(() -> new ProgramException("Program not found."));

        Program program = programRepository.findByIdAndUserId(programId, user.getId())
                .orElseThrow(() -> new ProgramException("Program not found."));

        List<ProgramTrainingDay> trainingDays = programTrainingDayRepository.findByProgramIdOrderByPositionAsc(programId);
        if (trainingDays.isEmpty()) {
            throw new ProgramException("Cannot start a program without training days.");
        }

        TrainingDay firstTrainingDay = trainingDays.getFirst().getTrainingDay();
        user.setActiveProgram(program);
        user.setActiveTrainingDay(firstTrainingDay);
        user.setActiveProgramWeek(1);
        userRepository.save(user);

        return new ActiveProgramSelection(program, loadTrainingDayWithExercises(firstTrainingDay), 1);
    }

    @Transactional
    public boolean deleteProgramForUser(Long programId, Long telegramUserId) {
        Optional<User> optionalUser = userRepository.findByTelegramId(telegramUserId);
        if (optionalUser.isEmpty()) {
            return false;
        }

        User user = optionalUser.get();
        Optional<Program> optionalProgram = programRepository.findByIdAndUserId(programId, user.getId());
        if (optionalProgram.isEmpty()) {
            return false;
        }

        Program program = optionalProgram.get();
        if (user.getActiveProgram() != null && programId.equals(user.getActiveProgram().getId())) {
            user.setActiveProgram(null);
            user.setActiveTrainingDay(null);
            user.setActiveProgramWeek(1);
            userRepository.save(user);
        }

        programTrainingDayRepository.deleteByProgramId(programId);
        programRepository.delete(program);
        return true;
    }

    @Transactional(readOnly = true)
    public TrainingDay getActiveTrainingDayForUser(Long telegramUserId) {
        return userRepository.findByTelegramId(telegramUserId)
                .map(User::getActiveTrainingDay)
                .map(this::loadTrainingDayWithExercises)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public int getActiveProgramWeekForUser(Long telegramUserId) {
        return userRepository.findByTelegramId(telegramUserId)
                .map(User::getActiveProgramWeek)
                .filter(week -> week != null && week > 0)
                .orElse(1);
    }

    @Transactional
    public ActiveTrainingDayProgression advanceActiveTrainingDayForUser(Long telegramUserId) {
        Optional<User> optionalUser = userRepository.findByTelegramId(telegramUserId);
        if (optionalUser.isEmpty()) {
            return null;
        }

        User user = optionalUser.get();
        Program activeProgram = user.getActiveProgram();
        if (activeProgram == null) {
            TrainingDay activeTrainingDay = user.getActiveTrainingDay();
            if (activeTrainingDay == null) {
                return null;
            }
            return new ActiveTrainingDayProgression(
                    loadTrainingDayWithExercises(activeTrainingDay),
                    normalizedWeek(user),
                    false,
                    false
            );
        }

        List<ProgramTrainingDay> trainingDays = programTrainingDayRepository.findByProgramIdOrderByPositionAsc(activeProgram.getId());
        if (trainingDays.isEmpty()) {
            TrainingDay activeTrainingDay = user.getActiveTrainingDay();
            if (activeTrainingDay == null) {
                return null;
            }
            return new ActiveTrainingDayProgression(
                    loadTrainingDayWithExercises(activeTrainingDay),
                    normalizedWeek(user),
                    false,
                    false
            );
        }

        TrainingDay currentTrainingDay = user.getActiveTrainingDay();
        int currentIndex = -1;
        if (currentTrainingDay != null) {
            for (int i = 0; i < trainingDays.size(); i++) {
                TrainingDay candidate = trainingDays.get(i).getTrainingDay();
                if (candidate != null && candidate.getId() != null && candidate.getId().equals(currentTrainingDay.getId())) {
                    currentIndex = i;
                    break;
                }
            }
        }

        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % trainingDays.size();
        boolean wrappedToFirstDay = currentIndex >= 0 && nextIndex == 0;
        int currentWeek = normalizedWeek(user);
        int nextWeek = wrappedToFirstDay ? currentWeek + 1 : currentWeek;
        TrainingDay nextTrainingDay = trainingDays.get(nextIndex).getTrainingDay();
        user.setActiveTrainingDay(nextTrainingDay);
        user.setActiveProgramWeek(nextWeek);
        userRepository.save(user);
        return new ActiveTrainingDayProgression(
                loadTrainingDayWithExercises(nextTrainingDay),
                nextWeek,
                wrappedToFirstDay,
                wrappedToFirstDay && nextWeek == 6
        );
    }

    private TrainingDay loadTrainingDayWithExercises(TrainingDay trainingDay) {
        if (trainingDay == null || trainingDay.getId() == null) {
            return trainingDay;
        }
        TrainingDay loadedTrainingDay = trainingDayRepository.findByIdWithExercises(trainingDay.getId()).orElse(trainingDay);
        if (loadedTrainingDay.getExercises() != null) {
            loadedTrainingDay.getExercises().forEach(exercise -> Hibernate.initialize(exercise.getVideoUrls()));
        }
        return loadedTrainingDay;
    }

    private int normalizedWeek(User user) {
        Integer week = user.getActiveProgramWeek();
        return week == null || week < 1 ? 1 : week;
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
