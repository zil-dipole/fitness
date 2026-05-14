package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.service.UserLanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for showing details of a specific training day
 */
@Component
public class ShowDayCommandHandler implements CallbackQueryHandler {

    private final TrainingDayService trainingDayService;
    private final UserLanguageService languageService;

    @Autowired
    public ShowDayCommandHandler(TrainingDayService trainingDayService,
                                 UserLanguageService languageService) {
        this.trainingDayService = trainingDayService;
        this.languageService = languageService;
    }

    public ShowDayCommandHandler(TrainingDayService trainingDayService) {
        this.trainingDayService = trainingDayService;
        this.languageService = null;
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
        var language = BotText.language(languageService, userId);

        // Extract training day ID from callback data
        Long trainingDayId;
        try {
            trainingDayId = Long.parseLong(data.substring("show_day_".length()));
        } catch (NumberFormatException e) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText(BotText.showDayInvalidId(language));
            return errorMessage;
        }

        // Get training day details
        TrainingDay trainingDay = trainingDayService.getTrainingDayById(trainingDayId);

        if (trainingDay == null) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText(BotText.showDayNotFound(language));
            return errorMessage;
        }

        // Check if the training day belongs to the user
        if (!trainingDay.getUser().getTelegramId().equals(userId)) {
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText(BotText.showDayUnauthorized(language));
            return errorMessage;
        }

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(TrainingDayMessageFormatter.format(trainingDay, language));
        sendMessage.setParseMode("HTML");

        return sendMessage;
    }
}
