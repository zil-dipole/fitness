package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.parser.TrainingDayParser;
import com.example.fitnessbot.repository.ExerciseRepository;
import com.example.fitnessbot.repository.TrainingDayRepository;
import com.example.fitnessbot.repository.UserRepository;
import org.springframework.stereotype.Service;

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
     */
    public TrainingDay processForwardedMessage(Long telegramUserId, String rawText) {
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
            for (Exercise exercise : exercises) {
                exercise.setTrainingDay(parsedTrainingDay);
            }
        }

        // 6. Save the training day (this will cascade to exercises)
        TrainingDay savedTrainingDay = trainingDayRepository.save(parsedTrainingDay);

        return savedTrainingDay;
    }
}
