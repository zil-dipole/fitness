package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.parser.OpenAiTrainingDayParser;
import com.example.fitnessbot.parser.TrainingDayParser;
import com.example.fitnessbot.parser.TrainingDayTitleNormalizer;
import com.example.fitnessbot.repository.ExerciseRepository;
import com.example.fitnessbot.repository.TrainingDayRepository;
import com.example.fitnessbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Service responsible for handling forwarded training‑day messages.
 * It parses the raw text, creates the domain objects and persists them.
 */
@Service
public class TrainingDayService {

    private static final Logger log = LoggerFactory.getLogger(TrainingDayService.class);
    private static final int MAX_NORMALIZED_EXERCISE_NAME_LENGTH = 255;

    private final TrainingDayParser parser;
    private final OpenAiTrainingDayParser openAiTrainingDayParser;
    private final UserRepository userRepository;
    private final TrainingDayRepository trainingDayRepository;
    private final ExerciseRepository exerciseRepository;

    public TrainingDayService(TrainingDayParser parser, OpenAiTrainingDayParser openAiTrainingDayParser,
                              UserRepository userRepository,
                              TrainingDayRepository trainingDayRepository, ExerciseRepository exerciseRepository) {
        this.parser = parser;
        this.openAiTrainingDayParser = openAiTrainingDayParser;
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
        return processForwardedMessage(telegramUserId, rawText, rawText);
    }

    /**
     * Process workout text while allowing a parser-specific representation.
     *
     * <p>The original raw text is kept on the saved entity. If the user has
     * AI parsing enabled, {@code aiParserRawText} is sent to the OpenAI parser.
     * This lets Excel uploads preserve spreadsheet rows for the AI parser while
     * keeping a deterministic parser-friendly text body as the stored source.</p>
     *
     * @param telegramUserId Telegram chat identifier
     * @param rawText text to save as the training-day source
     * @param aiParserRawText optional text to send to the OpenAI parser
     * @return the persisted TrainingDay entity
     * @throws IllegalArgumentException if the input is invalid
     */
    @Transactional
    public TrainingDay processForwardedMessage(Long telegramUserId, String rawText, String aiParserRawText) {
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
        String parserInput = rawText;
        if (user.isUseAiParser() && StringUtils.hasText(aiParserRawText)) {
            if (aiParserRawText.length() > 10000) {
                throw new IllegalArgumentException("OpenAI parser text is too large (max 10KB allowed)");
            }
            parserInput = aiParserRawText;
        }

        String parserName = user.isUseAiParser() ? "OpenAI" : "deterministic";
        log.info(
                "Parsing training day for Telegram user {} with {} parser (raw text {} chars, parser input {} chars)",
                telegramUserId,
                parserName,
                rawText.length(),
                parserInput.length()
        );

        TrainingDay parsedTrainingDay = user.isUseAiParser()
                ? openAiTrainingDayParser.parse(parserInput)
                : parser.parse(rawText);

        // 3. Set the user and raw text
        parsedTrainingDay.setUser(user);
        parsedTrainingDay.setRawText(rawText);

        // 4. Extract title from the first line
        String[] lines = rawText.split("\\r?\\n");
        if ((parsedTrainingDay.getTitle() == null || parsedTrainingDay.getTitle().isBlank()) && lines.length > 0) {
            parsedTrainingDay.setTitle(lines[0].trim());
        }
        parsedTrainingDay.setTitle(TrainingDayTitleNormalizer.normalize(parsedTrainingDay.getTitle()));

        // 5. Set the training day reference for exercises before saving
        List<Exercise> exercises = parsedTrainingDay.getExercises();
        if (exercises != null) {
            // Create a new list to avoid modifying the potentially lazy-loaded collection directly
            List<Exercise> exerciseList = new ArrayList<>(exercises);
            for (Exercise exercise : exerciseList) {
                exercise.setTrainingDay(parsedTrainingDay);
                String normalizedName = normalizeExerciseName(exercise.getName());
                exercise.setNormalizedName(normalizedName);
                findCanonicalExercise(user.getId(), normalizedName).ifPresent(canonicalExercise -> {
                    exercise.setCanonicalExercise(canonicalExercise);
                    if (exercise.getLastWeightKg() == null) {
                        exercise.setLastWeightKg(canonicalExercise.getLastWeightKg());
                    }
                });
            }
            // Set the modified list back to the training day
            parsedTrainingDay.setExercises(exerciseList);
        }

        // 6. Save the training day (this will cascade to exercises)
        TrainingDay savedTrainingDay = trainingDayRepository.save(parsedTrainingDay);
        if (savedTrainingDay.getExercises() != null) {
            savedTrainingDay.getExercises().forEach(exercise -> exercise.setTrainingDay(savedTrainingDay));
        }

        log.info(
                "Saved training day {} for Telegram user {} with {} parser ({} exercise(s))",
                savedTrainingDay.getId(),
                telegramUserId,
                parserName,
                savedTrainingDay.getExercises() == null ? 0 : savedTrainingDay.getExercises().size()
        );
        return savedTrainingDay;
    }

    private Optional<Exercise> findCanonicalExercise(Long userId, String normalizedName) {
        if (userId == null || !StringUtils.hasText(normalizedName)) {
            return Optional.empty();
        }

        return exerciseRepository.findCanonicalExercisesForUser(userId, normalizedName, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    public static String normalizeExerciseName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }

        String normalized = name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (normalized.codePointCount(0, normalized.length()) <= MAX_NORMALIZED_EXERCISE_NAME_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, normalized.offsetByCodePoints(0, MAX_NORMALIZED_EXERCISE_NAME_LENGTH));
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
