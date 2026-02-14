package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.TrainingDayService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for showing details of a specific training day
 */
@Component
public class ShowDayCommandHandler implements CallbackQueryHandler {

    private final TrainingDayService trainingDayService;

    public ShowDayCommandHandler(TrainingDayService trainingDayService) {
        this.trainingDayService = trainingDayService;
    }

    @Override
    public boolean canHandle(CallbackQuery callbackQuery) {
        return callbackQuery.getData().startsWith("show_day_");
    }

    @Transactional
    @Override
    public SendMessage handle(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Long userId = callbackQuery.getFrom().getId();

        // Extract training day ID from callback data
        Long trainingDayId;
        try {
            trainingDayId = Long.parseLong(data.substring("show_day_".length()));
        } catch (NumberFormatException e) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("Invalid training day ID.");
            return errorMessage;
        }

        // Get training day details
        TrainingDay trainingDay = trainingDayService.getTrainingDayById(trainingDayId);

        if (trainingDay == null) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("Training day not found.");
            return errorMessage;
        }

        // Check if the training day belongs to the user
        if (!trainingDay.getUser().getTelegramId().equals(userId)) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("You don't have permission to view this training day.");
            return errorMessage;
        }

        // Format response with training day details
        StringBuilder response = new StringBuilder();
        response.append("*").append(trainingDay.getTitle()).append("*\n\n");
        
        // Extract description from raw text (everything except the first line which is the title)
        if (trainingDay.getRawText() != null) {
            String[] lines = trainingDay.getRawText().split("\\r?\\n");
            // Skip the first line (title) and add the rest as description
            for (int i = 1; i < lines.length; i++) {
                response.append(lines[i]).append("\n");
            }
            response.append("\n");
        }

        if (trainingDay.getExercises() != null && !trainingDay.getExercises().isEmpty()) {
            response.append("Exercises:\n");
            for (int i = 0; i < trainingDay.getExercises().size(); i++) {
                Exercise exercise = trainingDay.getExercises().get(i);
                response.append((i + 1)).append(". ").append(exercise.getName()).append("\n");
                
                // Add sets and reps information
                if (exercise.getSets() != null && exercise.getRepsOrDuration() != null) {
                    response.append("   ").append(exercise.getSets()).append(" x ").append(exercise.getRepsOrDuration());
                } else if (exercise.getRepsOrDuration() != null) {
                    response.append("   ").append(exercise.getRepsOrDuration());
                }
                
                // Add weight if available (we don't have this information in the exercise yet)
                if (exercise.getLastWeightKg() != null) {
                    response.append(" @ ").append(exercise.getLastWeightKg()).append(" kg");
                }
                
                response.append("\n");
                
                // Add notes if available
                if (exercise.getNotes() != null && !exercise.getNotes().isEmpty()) {
                    response.append("   Notes: ").append(exercise.getNotes()).append("\n");
                }
                
                // Add URLs if available
                if (exercise.getVideoUrls() != null && !exercise.getVideoUrls().isEmpty()) {
                    response.append("   Videos:\n");
                    for (String url : exercise.getVideoUrls()) {
                        response.append("   - ").append(url).append("\n");
                    }
                }
                
                response.append("\n");
            }
        } else {
            response.append("No exercises defined for this training day.\n");
        }

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(response.toString());
        sendMessage.setParseMode("Markdown");

        return sendMessage;
    }
}