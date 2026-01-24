package com.example.fitnessbot.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@ConditionalOnProperty(name = "telegram.bot.token", matchIfMissing = false)
public class TelegramBotConfig {

    @Bean
    public TelegramBotsApi telegramBotsApi(FitnessTelegramBot fitnessTelegramBot) throws TelegramApiException {
        // Only register the bot if token and username are not empty
        if (fitnessTelegramBot.getBotToken() != null && !fitnessTelegramBot.getBotToken().isEmpty() &&
            fitnessTelegramBot.getBotUsername() != null && !fitnessTelegramBot.getBotUsername().isEmpty()) {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(fitnessTelegramBot);
            return botsApi;
        }
        return null;
    }
}
