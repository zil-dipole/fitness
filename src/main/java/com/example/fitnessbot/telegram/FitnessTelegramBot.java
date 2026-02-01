package com.example.fitnessbot.telegram;

import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.telegram.commands.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@ConditionalOnProperty(name = "telegram.bot.token")
public class FitnessTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(FitnessTelegramBot.class);

    private final TrainingDayService trainingDayService;
    private final List<CommandHandler> commandHandlers;

    private final String botUsername;

    public FitnessTelegramBot(TrainingDayService trainingDayService,
                              List<CommandHandler> commandHandlers,
                              @Value("${telegram.bot.token:}") String botToken,
                              @Value("${telegram.bot.username:}") String botUsername) {
        super(botToken);
        this.trainingDayService = trainingDayService;
        this.commandHandlers = commandHandlers;
        this.botUsername = botUsername;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Handle forwarded messages
        if (update.hasMessage() && update.getMessage().hasText() &&
                (update.getMessage().getForwardFrom() != null || update.getMessage().getForwardFromChat() != null)) {
            handleForwardedMessage(update);
        }
        // Handle commands
        else if (update.hasMessage() && update.getMessage().hasText() && update.getMessage().getText().startsWith("/")) {
            handleCommand(update);
        }
    }

    private void handleForwardedMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();

        log.info("Processing forwarded message from user {} with text length {}", userId, messageText.length());

        try {
            TrainingDay trainingDay = trainingDayService.processForwardedMessage(userId, messageText);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("✅ Training program received and processed successfully! Saved " +
                    trainingDay.getExercises().size() + " exercises.");

            sendTelegramMessage(sendMessage);
        } catch (Exception e) {
            log.error("Error processing forwarded message from user " + userId, e);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("❌ Sorry, there was an error processing your training program. Please try again.");

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        }
    }

    private void handleCommand(Update update) {
        String command = update.getMessage().getText();

        try {
            // Find the appropriate handler
            CommandHandler handler = commandHandlers.stream()
                    .filter(h -> h.canHandle(command))
                    .findFirst()
                    .orElse(null);

            // Handle the command
            SendMessage response;
            if (handler != null) {
                response = handler.handle(update);
            } else {
                response = new SendMessage();
                response.setChatId(update.getMessage().getChatId().toString());
                response.setText("Unknown command. Send /help for usage instructions.");
            }

            if (response != null) {
                sendTelegramMessage(response);
            }
        } catch (Exception e) {
            log.error("Failed to handle command: {}", command, e);

            try {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(update.getMessage().getChatId().toString());
                errorMessage.setText("Sorry, there was an error processing your command.");
                sendTelegramMessage(errorMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        }
    }

    /**
     * Wrapper method for sending Telegram messages to enable easier testing
     */
    protected void sendTelegramMessage(SendMessage sendMessage) throws Exception {
        execute(sendMessage);
    }
}
