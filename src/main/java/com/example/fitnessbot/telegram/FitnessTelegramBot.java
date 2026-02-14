package com.example.fitnessbot.telegram;

import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.telegram.commands.CallbackQueryHandler;
import com.example.fitnessbot.telegram.commands.CommandHandler;
import com.example.fitnessbot.telegram.commands.CommandMetadata;
import com.example.fitnessbot.telegram.commands.CommandRegistryService;
import com.example.fitnessbot.telegram.commands.ShowDayCommandHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

@Component
@ConditionalOnProperty(name = "telegram.bot.token")
public class FitnessTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(FitnessTelegramBot.class);

    private final TrainingDayService trainingDayService;
    private final ProgramCreationSessionManager sessionManager;
    private final List<CommandHandler> commandHandlers;
    private final List<CallbackQueryHandler> callbackQueryHandlers;
    private final CommandRegistryService commandRegistryService;

    private final String botUsername;

    public FitnessTelegramBot(TrainingDayService trainingDayService,
                              ProgramCreationSessionManager sessionManager,
                              List<CommandHandler> commandHandlers,
                              List<CallbackQueryHandler> callbackQueryHandlers,
                              CommandRegistryService commandRegistryService,
                              @Value("${telegram.bot.token:}") String botToken,
                              @Value("${telegram.bot.username:}") String botUsername) {
        super(botToken);
        this.trainingDayService = trainingDayService;
        this.sessionManager = sessionManager;
        this.commandHandlers = commandHandlers;
        this.callbackQueryHandlers = callbackQueryHandlers;
        this.commandRegistryService = commandRegistryService;
        this.botUsername = botUsername;
    }


    @PostConstruct
    public void registerCommands() {
        // Only register commands when not in test environment
        if (!"true".equals(System.getProperty("test.profile"))) {
            try {
                List<BotCommand> botCommands = commandHandlers.stream()
                        .map(handler -> new BotCommand(handler.getCommand(), handler.getCommandDescription()))
                        .toList();

                SetMyCommands setMyCommands = new SetMyCommands();

                setMyCommands.setCommands(botCommands);
                setMyCommands.setScope(new BotCommandScopeDefault());

                execute(setMyCommands);
            } catch (TelegramApiException e) {
                log.error("Failed to register bot commands", e);
            }
        }
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
        // Handle callback queries (button presses)
        else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    /**
     * Handle callback queries from inline keyboard buttons
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            // First, try to handle with registered callback query handlers
            for (CallbackQueryHandler handler : callbackQueryHandlers) {
                if (handler.canHandle(callbackQuery)) {
                    SendMessage message = handler.handle(new Update() {{
                        setCallbackQuery(callbackQuery);
                    }});
                    
                    sendTelegramMessage(message);
                    
                    // Acknowledge the callback query to remove loading indicator
                    if (callbackQuery.getId() != null) {
                        AnswerCallbackQuery answer = new AnswerCallbackQuery();
                        answer.setCallbackQueryId(callbackQuery.getId());
                        execute(answer);
                    }
                    
                    return;
                }
            }
            
            // If no handler matched, use the default handling
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());

            // Handle main menu callbacks (existing functionality)
            switch (callbackData) {
                case "create_program":
                    message.setText("To create a program, use the /create_program <name> command.\nExample: /create_program My Workout Plan");
                    break;
                case "view_programs":
                    message.setText("To view your programs, this feature will be implemented soon!");
                    break;
                case "help":
                    message.setText("""
                            Simply forward your workout program messages to me and I'll parse and save them.

                            Supported format:
                            - Section headers ending with ':'
                            - Exercises with bullet points ('⁃' or '-')
                            - Sets and reps like "3 x 10"
                            - Video links

                            Program Creation Commands:
                            - /create_program <name> - Start creating a new program
                            - Forward training day messages to add them to the program
                            - /finish_program - Finish and save the program
                            - /cancel_program - Cancel program creation

                            Example:
                            Upper Body:
                            - Bench Press 3 x 10 (Warm up set)
                            - https://youtube.com/watch?v=example""");
                    break;
                case "start_menu":
                    message.setText("Welcome to Fitness Bot! Choose an option below:");
                    message.setReplyMarkup(createMainMenuKeyboard());
                    break;
                default:
                    // Handle command suggestions (new functionality)
                    if (callbackData.startsWith("cmd:")) {
                        handleCommandSuggestion(callbackQuery);
                        return; // We've handled the callback, so we can return early
                    } else {
                        message.setText("Unknown button action. Please try again.");
                    }
                    break;
            }

            sendTelegramMessage(message);

            // Acknowledge the callback query to remove loading indicator
            // Only if callbackQueryId is not null (e.g., in real Telegram environment)
            if (callbackQuery.getId() != null) {
                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(callbackQuery.getId());
                // Skip actual Telegram API calls during testing
                if (!"true".equals(System.getProperty("test.profile"))) {
                    execute(answer);
                }
            }
        } catch (Exception e) {
            log.error("Error handling callback query: {}", callbackData, e);
            try {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId.toString());
                errorMessage.setText("Sorry, there was an error processing your request. Please try again.");
                sendTelegramMessage(errorMessage);

                // Acknowledge the callback query even in case of error
                if (callbackQuery.getId() != null) {
                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(callbackQuery.getId());
                    // Skip actual Telegram API calls during testing
                    if (!"true".equals(System.getProperty("test.profile"))) {
                        execute(answer);
                    }
                }
            } catch (Exception telegramException) {
                log.error("Failed to send error message for callback query: {}", callbackData, telegramException);
            }
        }
    }

    /**
     * Handle command suggestion selected from inline keyboard
     */
    private void handleCommandSuggestion(CallbackQuery callbackQuery) throws Exception {
        String callbackData = callbackQuery.getData();
        String command = callbackData.substring(4); // Remove "cmd:" prefix

        // Acknowledge the callback query
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        answer.setText("Executing: " + command);
        execute(answer);

        // Create a fake update to simulate the command being sent
        Update fakeUpdate = new Update();
        org.telegram.telegrambots.meta.api.objects.Message fakeMessage = new org.telegram.telegrambots.meta.api.objects.Message();
        fakeMessage.setText(command);

        // Cast to Message to access getChat() method
        if (callbackQuery.getMessage() instanceof org.telegram.telegrambots.meta.api.objects.Message originalMessage) {
            fakeMessage.setChat(originalMessage.getChat());
        }

        fakeMessage.setFrom(callbackQuery.getFrom());
        fakeUpdate.setMessage(fakeMessage);
        fakeUpdate.setUpdateId(1); // Set a dummy update ID

        // Handle the command
        handleCommand(fakeUpdate);
    }

    /**
     * Create the main menu inline keyboard
     */
    private InlineKeyboardMarkup createMainMenuKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // First row - Create Program and View Programs
        List<InlineKeyboardButton> firstRow = new ArrayList<>();
        InlineKeyboardButton createProgramBtn = new InlineKeyboardButton();
        createProgramBtn.setText("Create Program");
        createProgramBtn.setCallbackData("create_program");
        firstRow.add(createProgramBtn);

        InlineKeyboardButton viewProgramsBtn = new InlineKeyboardButton();
        viewProgramsBtn.setText("View Programs");
        viewProgramsBtn.setCallbackData("view_programs");
        firstRow.add(viewProgramsBtn);

        rows.add(firstRow);

        // Second row - Help
        List<InlineKeyboardButton> secondRow = new ArrayList<>();
        InlineKeyboardButton helpBtn = new InlineKeyboardButton();
        helpBtn.setText("Help");
        helpBtn.setCallbackData("help");
        secondRow.add(helpBtn);

        rows.add(secondRow);

        markup.setKeyboard(rows);
        return markup;
    }

    private void handleForwardedMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();

        // Check if user is in a program creation session
        if (sessionManager.hasActiveSession(userId)) {
            handleForwardedMessageDuringProgramCreation(update);
            return;
        }

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

    private void handleForwardedMessageDuringProgramCreation(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();

        try {
            // Process the training day normally
            TrainingDay trainingDay = trainingDayService.processForwardedMessage(userId, messageText);

            // Add it to the program creation session
            var session = sessionManager.getSession(userId);
            session.addTrainingDay(trainingDay);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("✅ Training day added to your program! (Total: " +
                               session.getTrainingDaysCount() + " days)");

            sendTelegramMessage(sendMessage);
        } catch (Exception e) {
            log.error("Error processing forwarded message during program creation for user " + userId, e);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("❌ Sorry, there was an error adding this training day to your program. Please try again.");

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        }
    }

    private void handleCommand(Update update) {
        String command = update.getMessage().getText();

        // Check for slash command to show all available commands
        if ("/".equals(command)) {
            showAllCommands(update);
            return;
        }

        // Check for partial commands and offer suggestions
        if (command.startsWith("/")) {
            // Check if this is exactly a known command
            boolean isKnownCommand = commandHandlers.stream()
                    .anyMatch(h -> h.canHandle(command));

            if (!isKnownCommand) {
                // This might be a partial command, show suggestions
                showCommandSuggestions(update, command);
                return;
            }
        }

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
     * Show all available commands when user types just "/"
     */
    private void showAllCommands(Update update) {
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId().toString());
        message.setText("📋 Available commands:");

        // Create inline keyboard with all commands
        InlineKeyboardMarkup markup = createCommandKeyboard(commandRegistryService.getAllCommands());
        message.setReplyMarkup(markup);

        try {
            sendTelegramMessage(message);
        } catch (Exception e) {
            log.error("Failed to send command list", e);
        }
    }

    /**
     * Show suggestions for partial command
     */
    private void showCommandSuggestions(Update update, String partialCommand) {
        List<CommandMetadata> suggestions = commandRegistryService.findCommandsByPrefix(partialCommand);

        // If no prefix matches, try similarity search
        if (suggestions.isEmpty() && partialCommand.length() > 2) {
            suggestions = commandRegistryService.findSimilarCommands(partialCommand);
        }

        if (!suggestions.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(update.getMessage().getChatId().toString());
            message.setText("❓ Did you mean one of these commands?");

            // Create inline keyboard with suggested commands
            InlineKeyboardMarkup markup = createCommandKeyboard(suggestions);
            message.setReplyMarkup(markup);

            try {
                sendTelegramMessage(message);
            } catch (Exception e) {
                log.error("Failed to send command suggestions", e);
            }
        } else {
            // No suggestions, send unknown command message
            SendMessage message = new SendMessage();
            message.setChatId(update.getMessage().getChatId().toString());
            message.setText("Unknown command. Send /help for usage instructions.");

            try {
                sendTelegramMessage(message);
            } catch (Exception e) {
                log.error("Failed to send unknown command message", e);
            }
        }
    }

    /**
     * Create inline keyboard markup for a list of commands
     */
    private InlineKeyboardMarkup createCommandKeyboard(List<CommandMetadata> commands) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Create rows with 2 commands per row
        for (int i = 0; i < commands.size(); i += 2) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            // First command in row
            InlineKeyboardButton button1 = new InlineKeyboardButton();
            button1.setText(commands.get(i).getCommand());
            button1.setCallbackData("cmd:" + commands.get(i).getCommand());
            row.add(button1);

            // Second command in row (if exists)
            if (i + 1 < commands.size()) {
                InlineKeyboardButton button2 = new InlineKeyboardButton();
                button2.setText(commands.get(i + 1).getCommand());
                button2.setCallbackData("cmd:" + commands.get(i + 1).getCommand());
                row.add(button2);
            }

            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Wrapper method for sending Telegram messages to enable easier testing
     */
    protected void sendTelegramMessage(SendMessage sendMessage) throws Exception {
        // Skip actual Telegram API calls during testing
        if (!"true".equals(System.getProperty("test.profile"))) {
            execute(sendMessage);
        }
    }
}