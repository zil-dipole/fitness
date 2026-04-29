package com.example.fitnessbot.telegram;

import com.example.fitnessbot.exception.TrainingDayException;
import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.exception.WorkoutException;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramRenameSessionManager;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.service.WorkoutService;
import com.example.fitnessbot.telegram.commands.CallbackQueryHandler;
import com.example.fitnessbot.telegram.commands.CommandHandler;
import com.example.fitnessbot.telegram.commands.CommandMetadata;
import com.example.fitnessbot.telegram.commands.CommandRegistryService;
import com.example.fitnessbot.telegram.commands.ContextAwareCommandHandler;
import com.example.fitnessbot.telegram.commands.WorkoutMessageFormatter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllPrivateChats;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.ArrayList;

@Component
@ConditionalOnProperty(name = "telegram.bot.token")
public class FitnessTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(FitnessTelegramBot.class);

    // Number of commands to display per row in the command keyboard
    private static final int COMMANDS_PER_ROW = 2;

    private final TrainingDayService trainingDayService;
    private final WorkoutService workoutService;
    private final ProgramService programService;
    private final ProgramCreationSessionManager sessionManager;
    private final ProgramRenameSessionManager renameSessionManager;
    private final List<CommandHandler> commandHandlers;
    private final List<CallbackQueryHandler> callbackQueryHandlers;
    private final CommandRegistryService commandRegistryService;
    private final MenuKeyboardFactory menuKeyboardFactory;

    private final String botUsername;

    public FitnessTelegramBot(TrainingDayService trainingDayService,
                              WorkoutService workoutService,
                              ProgramService programService,
                              ProgramCreationSessionManager sessionManager,
                              ProgramRenameSessionManager renameSessionManager,
                              List<CommandHandler> commandHandlers,
                              List<CallbackQueryHandler> callbackQueryHandlers,
                              CommandRegistryService commandRegistryService,
                              MenuKeyboardFactory menuKeyboardFactory,
                              @Value("${telegram.bot.token:}") String botToken,
                              @Value("${telegram.bot.username:}") String botUsername) {
        super(botToken);
        this.trainingDayService = trainingDayService;
        this.workoutService = workoutService;
        this.programService = programService;
        this.sessionManager = sessionManager;
        this.renameSessionManager = renameSessionManager;
        this.commandHandlers = commandHandlers;
        this.callbackQueryHandlers = callbackQueryHandlers;
        this.commandRegistryService = commandRegistryService;
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.botUsername = botUsername;
    }


    @PostConstruct
    public void registerCommands() {
        if ("true".equals(System.getProperty("test.profile"))) {
            log.info("Skipping Telegram command menu registration in test profile");
            return;
        }

        List<BotCommand> commands = createTelegramCommandMenu();
        try {
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
            execute(new SetMyCommands(commands, new BotCommandScopeAllPrivateChats(), null));
            log.info("Registered Telegram command menu with {} globally visible commands", commands.size());
        } catch (Exception e) {
            log.warn("Failed to register Telegram command menu", e);
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
        // Handle plain workout input, such as weight entries for the current set
        else if (update.hasMessage() && update.getMessage().hasText()) {
            handlePlainTextMessage(update);
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
                    
                    acknowledgeCallbackQuery(callbackQuery, null);
                    
                    return;
                }
            }
            
            // If no handler matched, use the default handling
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());

            // Handle main menu callbacks (existing functionality)
            switch (callbackData) {
                case "create_program":
                    handleCommandCallback(callbackQuery, "/create_program");
                    acknowledgeCallbackQuery(callbackQuery, null);
                    return;
                case "view_programs":
                    handleCommandCallback(callbackQuery, "/show_program");
                    acknowledgeCallbackQuery(callbackQuery, null);
                    return;
                case "cancel_program":
                    handleCommandCallback(callbackQuery, "/cancel_program");
                    acknowledgeCallbackQuery(callbackQuery, null);
                    return;
                case "finish_program":
                    handleCommandCallback(callbackQuery, "/finish_program");
                    acknowledgeCallbackQuery(callbackQuery, null);
                    return;
                case "help":
                    handleCommandCallback(callbackQuery, "/help");
                    acknowledgeCallbackQuery(callbackQuery, null);
                    return;
                case "start_menu":
                    handleCommandCallback(callbackQuery, "/menu");
                    acknowledgeCallbackQuery(callbackQuery, null);
                    return;
                default:
                    // Handle command suggestions (new functionality)
                    if (callbackData.startsWith("show_program:")) {
                        handleCommandCallback(callbackQuery, "/show_program " + callbackData.substring("show_program:".length()));
                        acknowledgeCallbackQuery(callbackQuery, null);
                        return;
                    } else if (callbackData.startsWith("rename_program:")) {
                        SendMessage renamePrompt = buildRenameProgramPrompt(callbackQuery);
                        sendTelegramMessage(renamePrompt);
                        acknowledgeCallbackQuery(callbackQuery, null);
                        return;
                    } else if (callbackData.startsWith("cmd:")) {
                        handleCommandSuggestion(callbackQuery);
                        return; // We've handled the callback, so we can return early
                    } else {
                        message.setText("I couldn't understand that button action. Please try again.");
                    }
                    break;
            }

            sendTelegramMessage(message);

            acknowledgeCallbackQuery(callbackQuery, null);
        } catch (Exception e) {
            log.error("Error handling callback query: {}", callbackData, e);
            try {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId.toString());
                errorMessage.setText("Sorry, there was an error processing your request. Please try again.");
                sendTelegramMessage(errorMessage);

                acknowledgeCallbackQuery(callbackQuery, null);
            } catch (Exception telegramException) {
                log.warn("Failed to acknowledge callback query: {} for callback data: {}. Error: {}", 
                        callbackQuery.getId(), callbackData, telegramException.getMessage());
            }
        }
    }

    /**
     * Handle command suggestion selected from inline keyboard
     */
    private void handleCommandSuggestion(CallbackQuery callbackQuery) throws Exception {
        String callbackData = callbackQuery.getData();
        String command = callbackData.substring(4); // Remove "cmd:" prefix

        acknowledgeCallbackQuery(callbackQuery, "Executing: " + command);
        handleCommandCallback(callbackQuery, command);
    }

    private void handleCommandCallback(CallbackQuery callbackQuery, String command) {
        Update fakeUpdate = new Update();
        org.telegram.telegrambots.meta.api.objects.Message fakeMessage = new org.telegram.telegrambots.meta.api.objects.Message();
        fakeMessage.setText(command);

        if (callbackQuery.getMessage() instanceof org.telegram.telegrambots.meta.api.objects.Message originalMessage) {
            fakeMessage.setChat(originalMessage.getChat());
            if (fakeMessage.getChat() == null) {
                org.telegram.telegrambots.meta.api.objects.Chat chat = new org.telegram.telegrambots.meta.api.objects.Chat();
                chat.setId(originalMessage.getChatId());
                fakeMessage.setChat(chat);
            }
        }

        fakeMessage.setFrom(callbackQuery.getFrom());
        fakeUpdate.setMessage(fakeMessage);
        fakeUpdate.setUpdateId(1);

        handleCommand(fakeUpdate);
    }

    /**
     * Create the main menu inline keyboard
     *
     * @param userId The user ID to determine if they have an active session
     */
    public InlineKeyboardMarkup createMainMenuKeyboard(Long userId) {
        return menuKeyboardFactory.createMainMenuKeyboard(userId);
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
            sendMessage.setText("✅ Training day saved.\n" +
                    "Parsed " + trainingDay.getExercises().size() + " exercise" +
                    (trainingDay.getExercises().size() == 1 ? "" : "s") + ".");

            sendTelegramMessage(sendMessage);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input when processing forwarded message from user {}: {}", userId, e.getMessage());

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("❌ I couldn't use that message.\n" + e.getMessage());

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        } catch (TrainingDayException e) {
            log.warn("Parser error when processing forwarded message from user {}: {}", userId, e.getMessage());

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("❌ I couldn't parse that training day.\n" + e.getMessage());

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Database error when processing forwarded message from user " + userId, e);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("❌ I couldn't save that training day because of a database error.\nPlease try again.");

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        } catch (Exception e) {
            log.error("Unexpected error processing forwarded message from user " + userId, e);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("❌ Something went wrong while saving that training day.\nPlease try again.");

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        }
    }

    private void handleForwardedMessageDuringProgramCreation(Update update) {
        handleTrainingDayMessageDuringProgramCreation(update);
    }

    private void handleTrainingDayMessageDuringProgramCreation(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();

        try {
            TrainingDay trainingDay = trainingDayService.processForwardedMessage(userId, messageText);

            var session = sessionManager.getSession(userId);
            session.addTrainingDay(trainingDay);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            String trainingDayTitle = trainingDay.getTitle() == null || trainingDay.getTitle().isBlank()
                    ? "Training day"
                    : "\"" + trainingDay.getTitle().trim() + "\"";
            sendMessage.setText("✅ Added " + trainingDayTitle + " to \"" + session.getProgram().getName() + "\".\n" +
                    session.getTrainingDaysCount() + " training " +
                    (session.getTrainingDaysCount() == 1 ? "day" : "days") +
                    " in this draft.\n\n" +
                    "Send or forward another day, or tap \"Finish Program\" when you're done.");
            sendMessage.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(userId));

            sendTelegramMessage(sendMessage);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid training day input during program creation for user {}: {}", userId, e.getMessage());

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("❌ I couldn't use that training day.\n" + e.getMessage());

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        } catch (TrainingDayException e) {
            log.warn("Parser error during program creation for user {}: {}", userId, e.getMessage());

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("❌ I couldn't parse that training day.\n" + e.getMessage());

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        } catch (Exception e) {
            log.error("Error processing forwarded message during program creation for user " + userId, e);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText("❌ I couldn't add that training day to your draft.\nPlease try again.");

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        }
    }

    private void handlePlainTextMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        if (sessionManager.hasActiveSession(userId)) {
            handleTrainingDayMessageDuringProgramCreation(update);
            return;
        }

        if (renameSessionManager.hasPendingRename(userId)) {
            handleProgramRenameMessage(update);
            return;
        }

        if (!workoutService.hasWorkoutInputContext(userId)) {
            return;
        }

        SendMessage response = new SendMessage();
        response.setChatId(update.getMessage().getChatId().toString());
        try {
            WorkoutService.WeightEntryResult result = workoutService.recordWeightForCurrentSet(
                    userId,
                    update.getMessage().getText()
            );
            if (result.dayCompleted() || !result.accepted()) {
                response.setText(result.message());
            } else {
                response.setText(WorkoutMessageFormatter.formatExerciseResult(result.message(), result.exerciseView()));
                response.setParseMode("HTML");
                response.setReplyMarkup(WorkoutMessageFormatter.exerciseKeyboard(result.exerciseView()));
            }

            sendTelegramMessage(response);
        } catch (WorkoutException e) {
            response.setText(e.getMessage());
            try {
                sendTelegramMessage(response);
            } catch (Exception telegramApiException) {
                log.error("Failed to send workout error message to user", telegramApiException);
            }
        } catch (Exception e) {
            log.error("Failed to handle workout input for user {}", userId, e);
            response.setText("Sorry, there was an error saving your workout set.");
            try {
                sendTelegramMessage(response);
            } catch (Exception telegramApiException) {
                log.error("Failed to send workout error message to user", telegramApiException);
            }
        }
    }

    private SendMessage buildRenameProgramPrompt(CallbackQuery callbackQuery) {
        SendMessage response = new SendMessage();
        response.setChatId(callbackQuery.getMessage().getChatId().toString());

        Long programId;
        try {
            programId = Long.parseLong(callbackQuery.getData().substring("rename_program:".length()));
        } catch (NumberFormatException e) {
            response.setText("Invalid program ID.");
            return response;
        }

        var program = programService.getProgramForUser(programId, callbackQuery.getFrom().getId());
        if (program.isEmpty()) {
            response.setText("I couldn't find that program.");
            return response;
        }

        renameSessionManager.startRename(callbackQuery.getFrom().getId(), programId);
        response.setText("Send the new name for \"" + program.get().getName() + "\".");
        return response;
    }

    private void handleProgramRenameMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long programId = renameSessionManager.getProgramId(userId);
        if (programId == null) {
            renameSessionManager.endRename(userId);
            return;
        }

        SendMessage response = new SendMessage();
        response.setChatId(update.getMessage().getChatId().toString());

        try {
            var program = programService.renameProgramForUser(programId, userId, update.getMessage().getText());
            renameSessionManager.endRename(userId);
            response.setText("✅ Program renamed to \"" + program.getName() + "\".\n\nUse /show_program " + program.getId() + " to open it.");
        } catch (ProgramException e) {
            if ("Program name can't be empty.".equals(e.getMessage())) {
                response.setText("Program name can't be empty.\n\nSend a new name.");
            } else {
                renameSessionManager.endRename(userId);
                response.setText(e.getMessage());
            }
        } catch (Exception e) {
            renameSessionManager.endRename(userId);
            log.error("Failed to rename program for user {}", userId, e);
            response.setText("I couldn't rename that program.\nPlease try again.");
        }

        try {
            sendTelegramMessage(response);
        } catch (Exception telegramApiException) {
            log.error("Failed to send rename program message to user", telegramApiException);
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
                // Check if it's a context-aware handler and if it's available
                if (handler instanceof ContextAwareCommandHandler contextAwareHandler) {
                    Long userId = update.getMessage().getFrom().getId();
                    if (!contextAwareHandler.isAvailable(userId, sessionManager)) {
                        response = contextAwareHandler.handleUnavailable(update);
                    } else {
                        response = handler.handle(update);
                    }
                } else {
                    response = handler.handle(update);
                }
            } else {
                response = new SendMessage();
                response.setChatId(update.getMessage().getChatId().toString());
                response.setText("I don't recognize that command.\n\nSend /help to see what I can do.");
            }

            if (response != null) {
                sendTelegramMessage(response);
            }
        } catch (Exception e) {
            log.error("Failed to handle command: {}", command, e);

            try {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(update.getMessage().getChatId().toString());
                errorMessage.setText("I couldn't process that command.\nPlease try again.");
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
        Long userId = update.getMessage().getFrom().getId();
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId().toString());
        message.setText("Choose a command:");

        // Filter commands to only include available commands for context-aware handlers
        List<CommandMetadata> availableCommands = commandRegistryService.getAllCommands().stream()
                .filter(cmd -> isCommandAvailable(cmd, userId))
                .toList();

        // Create inline keyboard with available commands
        InlineKeyboardMarkup markup = createCommandKeyboard(availableCommands);
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
        Long userId = update.getMessage().getFrom().getId();
        List<CommandMetadata> suggestions = commandRegistryService.findCommandsByPrefix(partialCommand);

        // If no prefix matches, try similarity search
        if (suggestions.isEmpty() && partialCommand.length() > 2) {
            suggestions = commandRegistryService.findSimilarCommands(partialCommand);
        }

        // Filter suggestions to only include available commands for context-aware handlers
        suggestions = suggestions.stream()
                .filter(cmd -> isCommandAvailable(cmd, userId))
                .toList();

        if (!suggestions.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(update.getMessage().getChatId().toString());
            message.setText("Did you mean one of these commands?");

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
            message.setText("I don't recognize that command.\n\nSend /help to see what I can do.");

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

        // Create rows with COMMANDS_PER_ROW commands per row
        for (int i = 0; i < commands.size(); i += COMMANDS_PER_ROW) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            // First command in row
            InlineKeyboardButton button1 = new InlineKeyboardButton();
            button1.setText(commands.get(i).getCommand());
            button1.setCallbackData("cmd:" + commands.get(i).getCommand());
            row.add(button1);

            // Additional commands in row (if exist)
            for (int j = 1; j < COMMANDS_PER_ROW && i + j < commands.size(); j++) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(commands.get(i + j).getCommand());
                button.setCallbackData("cmd:" + commands.get(i + j).getCommand());
                row.add(button);
            }

            rows.add(row);
        }

        markup.setKeyboard(rows);
        return markup;
    }

    private boolean isCommandAvailable(CommandMetadata commandMetadata, Long userId) {
        CommandHandler handler = commandHandlers.stream()
                .filter(h -> h.canHandle(commandMetadata.getCommand()))
                .findFirst()
                .orElse(null);

        if (handler == null) {
            return false;
        }

        if (handler instanceof ContextAwareCommandHandler contextAwareHandler) {
            return contextAwareHandler.isAvailable(userId, sessionManager);
        }

        return true;
    }

    List<BotCommand> createTelegramCommandMenu() {
        return List.of(
                new BotCommand("start", "Start the bot"),
                new BotCommand("help", "Show help"),
                new BotCommand("menu", "Show main menu"),
                new BotCommand("create_program", "Create a workout program"),
                new BotCommand("show_program", "Show saved programs"),
                new BotCommand("active_day", "Show active training day")
        );
    }

    /**
     * Wrapper method for sending Telegram messages to enable easier testing
     */
    protected void sendTelegramMessage(SendMessage sendMessage) throws Exception {
        // Skip actual Telegram API calls during testing
        if (!"true".equals(System.getProperty("test.profile"))) {
            try {
                execute(sendMessage);
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                log.error("Telegram API error when sending message to chat {}: {}", 
                         sendMessage.getChatId(), e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                log.error("Unexpected error when sending message to chat {}: {}", 
                         sendMessage.getChatId(), e.getMessage(), e);
                throw e;
            }
        }
    }

    private void acknowledgeCallbackQuery(CallbackQuery callbackQuery, String text) {
        if (callbackQuery.getId() == null || "true".equals(System.getProperty("test.profile"))) {
            return;
        }

        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            answer.setText(text);
            execute(answer);
        } catch (Exception e) {
            log.warn("Failed to acknowledge callback query: {} for callback data: {}. Error: {}",
                    callbackQuery.getId(), callbackQuery.getData(), e.getMessage());
        }
    }
}
