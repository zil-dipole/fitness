package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.parser.TrainingDayParser;
import com.example.fitnessbot.repository.ExerciseRepository;
import com.example.fitnessbot.repository.TrainingDayRepository;
import com.example.fitnessbot.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for handling forwarded training‑day messages.
 * It parses the raw text, creates the domain objects and persists them.
 */
@Service
public class TrainingDayService {

    private final TrainingDayParser parser;
    private final UserRepository userRepository;
    private final TrainingDayRepository trainingDayRepository;
    private final ExerciseRepository exerciseRepository;

    public TrainingDayService(TrainingDayParser parser, UserRepository userRepository,
                              TrainingDayRepository trainingDayRepository, ExerciseRepository exerciseRepository) {
        this.parser = parser;
        this.userRepository = userRepository;
        this.trainingDayRepository = trainingDayRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Process a forwarded message text for a given user.
     * @param telegramUserId Telegram chat identifier
     * @param rawText the full forwarded message containing the workout description
     * @return the persisted TrainingDay entity (or its id)
     * @throws IllegalArgumentException if the input is invalid
     */
    @Transactional
    public TrainingDay processForwardedMessage(Long telegramUserId, String rawText) {
        // Validate input
        if (telegramUserId == null) {
            throw new IllegalArgumentException("Telegram user ID cannot be null");
        }
        if (rawText == null || rawText.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw text cannot be null or empty");
        }
        // Limit input size to prevent abuse
        if (rawText.length() > 10000) { // 10KB limit
            throw new IllegalArgumentException("Raw text is too large (max 10KB allowed)");
        }
        // 1. Find or create the user
        User user = userRepository.findByTelegramId(telegramUserId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramId(telegramUserId);
                    // Name and weight will be set later through user profile management
                    return userRepository.save(newUser);
                });

        // 2. Parse the raw text into a structured model
        TrainingDay parsedTrainingDay = parser.parse(rawText);

        // 3. Set the user and raw text
        parsedTrainingDay.setUser(user);
        parsedTrainingDay.setRawText(rawText);

        // 4. Extract title from the first line
        String[] lines = rawText.split("\\r?\\n");
        if (lines.length > 0) {
            parsedTrainingDay.setTitle(lines[0].trim());
        }

        // 5. Set the training day reference for exercises before saving
        List<Exercise> exercises = parsedTrainingDay.getExercises();
        if (exercises != null) {
            // Create a new list to avoid modifying the potentially lazy-loaded collection directly
            List<Exercise> exerciseList = new ArrayList<>(exercises);
            for (Exercise exercise : exerciseList) {
                exercise.setTrainingDay(parsedTrainingDay);
            }
            // Set the modified list back to the training day
            parsedTrainingDay.setExercises(exerciseList);
        }

        // 6. Save the training day (this will cascade to exercises)
        TrainingDay savedTrainingDay = trainingDayRepository.save(parsedTrainingDay);
        if (savedTrainingDay.getExercises() != null) {
            savedTrainingDay.getExercises().forEach(exercise -> exercise.setTrainingDay(savedTrainingDay));
        }

        return savedTrainingDay;
    }
    
    /**
     * Get a training day by its ID
     * @param id The ID of the training day
     * @return The training day, or null if not found
     */
    public TrainingDay getTrainingDayById(Long id) {
        return trainingDayRepository.findById(id).orElse(null);
    }
}
