package com.example.fitnessbot.telegram;

import com.example.fitnessbot.exception.TrainingDayException;
import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.UserLanguage;
import com.example.fitnessbot.exception.WorkoutException;
import com.example.fitnessbot.parser.TrainingDayWorkbookParser;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramRenameSessionManager;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.TelegramUserProfileService;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.service.WorkoutService;
import com.example.fitnessbot.telegram.commands.CallbackQueryHandler;
import com.example.fitnessbot.telegram.commands.CommandHandler;
import com.example.fitnessbot.telegram.commands.CommandMetadata;
import com.example.fitnessbot.telegram.commands.CommandRegistryService;
import com.example.fitnessbot.telegram.commands.ContextAwareCommandHandler;
import com.example.fitnessbot.telegram.commands.BotText;
import com.example.fitnessbot.telegram.commands.WorkoutMessageFormatter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllPrivateChats;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "telegram.bot.token")
public class FitnessTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(FitnessTelegramBot.class);

    // Number of commands to display per row in the command keyboard
    private static final int COMMANDS_PER_ROW = 2;
    private static final long MAX_EXCEL_DOCUMENT_SIZE_BYTES = 5L * 1024L * 1024L;

    private final TrainingDayService trainingDayService;
    private final TrainingDayWorkbookParser workbookParser;
    private final WorkoutService workoutService;
    private final ProgramService programService;
    private final ProgramCreationSessionManager sessionManager;
    private final ProgramRenameSessionManager renameSessionManager;
    private final List<CommandHandler> commandHandlers;
    private final List<CallbackQueryHandler> callbackQueryHandlers;
    private final CommandRegistryService commandRegistryService;
    private final MenuKeyboardFactory menuKeyboardFactory;
    private final UserLanguageService languageService;
    private final TelegramUserProfileService userProfileService;

    private final String botUsername;

    @Autowired
    public FitnessTelegramBot(TrainingDayService trainingDayService,
                              TrainingDayWorkbookParser workbookParser,
                              WorkoutService workoutService,
                              ProgramService programService,
                              ProgramCreationSessionManager sessionManager,
                              ProgramRenameSessionManager renameSessionManager,
                              List<CommandHandler> commandHandlers,
                              List<CallbackQueryHandler> callbackQueryHandlers,
                              CommandRegistryService commandRegistryService,
                              MenuKeyboardFactory menuKeyboardFactory,
                              UserLanguageService languageService,
                              TelegramUserProfileService userProfileService,
                              @Value("${telegram.bot.token:}") String botToken,
                              @Value("${telegram.bot.username:}") String botUsername) {
        super(botToken);
        this.trainingDayService = trainingDayService;
        this.workbookParser = workbookParser;
        this.workoutService = workoutService;
        this.programService = programService;
        this.sessionManager = sessionManager;
        this.renameSessionManager = renameSessionManager;
        this.commandHandlers = commandHandlers;
        this.callbackQueryHandlers = callbackQueryHandlers;
        this.commandRegistryService = commandRegistryService;
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = languageService;
        this.userProfileService = userProfileService;
        this.botUsername = botUsername;
    }

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
        this(
                trainingDayService,
                new TrainingDayWorkbookParser(),
                workoutService,
                programService,
                sessionManager,
                renameSessionManager,
                commandHandlers,
                callbackQueryHandlers,
                commandRegistryService,
                menuKeyboardFactory,
                null,
                null,
                botToken,
                botUsername
        );
    }


    @PostConstruct
    public void registerCommands() {
        if ("true".equals(System.getProperty("test.profile"))) {
            log.info("Skipping Telegram command menu registration in test profile");
            return;
        }

        List<BotCommand> commands = createTelegramCommandMenu(UserLanguage.ENGLISH);
        List<BotCommand> russianCommands = createTelegramCommandMenu(UserLanguage.RUSSIAN);
        try {
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
            execute(new SetMyCommands(commands, new BotCommandScopeAllPrivateChats(), null));
            execute(new SetMyCommands(russianCommands, new BotCommandScopeDefault(), UserLanguage.RUSSIAN.getCode()));
            execute(new SetMyCommands(russianCommands, new BotCommandScopeAllPrivateChats(), UserLanguage.RUSSIAN.getCode()));
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
        recordTelegramUser(update);

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
        // Handle uploaded files
        else if (update.hasMessage() && update.getMessage().hasDocument()) {
            handleDocumentMessage(update);
        }
        // Handle plain workout input, such as weight entries for the current set
        else if (update.hasMessage() && update.getMessage().hasText()) {
            handlePlainTextMessage(update);
        }
    }

    private void recordTelegramUser(Update update) {
        if (userProfileService == null || update == null) {
            return;
        }

        org.telegram.telegrambots.meta.api.objects.User telegramUser = null;
        if (update.hasMessage() && update.getMessage() != null) {
            telegramUser = update.getMessage().getFrom();
        } else if (update.hasCallbackQuery() && update.getCallbackQuery() != null) {
            telegramUser = update.getCallbackQuery().getFrom();
        }

        if (telegramUser == null) {
            return;
        }

        try {
            userProfileService.recordTelegramUser(telegramUser);
        } catch (Exception e) {
            log.warn("Failed to record Telegram profile for user {}", telegramUser.getId(), e);
        }
    }

    /**
     * Handle callback queries from inline keyboard buttons
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        var language = BotText.language(languageService, callbackQuery.getFrom().getId());

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
                case "language":
                    handleCommandCallback(callbackQuery, "/language");
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
                        message.setText(BotText.callbackUnknown(language));
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
                errorMessage.setText(BotText.callbackError(language));
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

        var language = BotText.language(languageService, callbackQuery.getFrom().getId());
        acknowledgeCallbackQuery(callbackQuery, BotText.executingCommand(command, language));
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
        var language = BotText.language(languageService, userId);

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
            sendMessage.setText(BotText.trainingDaySaved(trainingDay.getExercises().size(), language));

            sendTelegramMessage(sendMessage);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input when processing forwarded message from user {}: {}", userId, e.getMessage());

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText(BotText.invalidTrainingDayMessage(language) + e.getMessage());

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        } catch (TrainingDayException e) {
            log.warn("Parser error when processing forwarded message from user {}: {}", userId, e.getMessage());

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText(BotText.parseTrainingDayError(language) + e.getMessage());

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Database error when processing forwarded message from user " + userId, e);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText(BotText.databaseTrainingDayError(language));

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        } catch (Exception e) {
            log.error("Unexpected error processing forwarded message from user " + userId, e);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText(BotText.unexpectedTrainingDaySaveError(language));

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
        var language = BotText.language(languageService, userId);

        try {
            TrainingDay trainingDay = trainingDayService.processForwardedMessage(userId, messageText);

            var session = sessionManager.getSession(userId);
            session.addTrainingDay(trainingDay);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            String trainingDayTitle = trainingDay.getTitle() == null || trainingDay.getTitle().isBlank()
                    ? BotText.fallbackTrainingDayTitle(language)
                    : "\"" + trainingDay.getTitle().trim() + "\"";
            sendMessage.setText(BotText.addedTrainingDayToDraft(
                    trainingDayTitle,
                    session.getProgram().getName(),
                    session.getTrainingDaysCount(),
                    language
            ));
            sendMessage.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(userId));

            sendTelegramMessage(sendMessage);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid training day input during program creation for user {}: {}", userId, e.getMessage());

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText(BotText.invalidTrainingDayDuringProgram(language) + e.getMessage());

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        } catch (TrainingDayException e) {
            log.warn("Parser error during program creation for user {}: {}", userId, e.getMessage());

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText(BotText.parseTrainingDayError(language) + e.getMessage());

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        } catch (Exception e) {
            log.error("Error processing forwarded message during program creation for user " + userId, e);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(update.getMessage().getChatId().toString());
            sendMessage.setText(BotText.addTrainingDayToDraftError(language));

            try {
                sendTelegramMessage(sendMessage);
            } catch (Exception telegramApiException) {
                log.error("Failed to send error message to user", telegramApiException);
            }
        }
    }

    private void handleDocumentMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        Document document = update.getMessage().getDocument();
        var language = BotText.language(languageService, userId);

        SendMessage response = new SendMessage();
        response.setChatId(chatId.toString());

        if (!sessionManager.hasActiveSession(userId)) {
            response.setText(BotText.excelUploadNeedsDraft(language));
            sendDocumentResponse(response);
            return;
        }

        if (!isExcelDocument(document)) {
            response.setText(BotText.excelUploadUnsupported(language));
            sendDocumentResponse(response);
            return;
        }

        Long fileSize = document.getFileSize();
        if (fileSize != null && fileSize > MAX_EXCEL_DOCUMENT_SIZE_BYTES) {
            response.setText(BotText.excelUploadTooLarge(language));
            sendDocumentResponse(response);
            return;
        }

        try (InputStream inputStream = downloadTelegramDocument(document)) {
            List<TrainingDayWorkbookParser.WorkbookTrainingDay> workbookTrainingDays = workbookParser.parse(inputStream);
            var session = sessionManager.getSession(userId);

            int importedCount = 0;
            for (TrainingDayWorkbookParser.WorkbookTrainingDay workbookTrainingDay : workbookTrainingDays) {
                TrainingDay trainingDay = trainingDayService.processForwardedMessage(
                        userId,
                        workbookTrainingDay.rawText(),
                        workbookTrainingDay.aiRawText()
                );
                session.addTrainingDay(trainingDay);
                importedCount++;
            }

            response.setText(BotText.excelUploadImported(
                    importedCount,
                    documentFileName(document),
                    session.getProgram().getName(),
                    session.getTrainingDaysCount(),
                    language
            ));
            response.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(userId));
            sendTelegramMessage(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid Excel training day upload from user {}: {}", userId, e.getMessage());
            response.setText(BotText.excelUploadParseError(language) + e.getMessage());
            sendDocumentResponse(response);
        } catch (TrainingDayException e) {
            log.warn("Parser error for Excel training day upload from user {}: {}", userId, e.getMessage());
            response.setText(BotText.parseTrainingDayError(language) + e.getMessage());
            sendDocumentResponse(response);
        } catch (Exception e) {
            log.error("Failed to process Excel training day upload for user " + userId, e);
            response.setText(BotText.excelUploadGenericError(language));
            sendDocumentResponse(response);
        }
    }

    protected InputStream downloadTelegramDocument(Document document) throws Exception {
        org.telegram.telegrambots.meta.api.objects.File telegramFile = execute(new GetFile(document.getFileId()));
        return downloadFileAsStream(telegramFile);
    }

    private void sendDocumentResponse(SendMessage response) {
        try {
            sendTelegramMessage(response);
        } catch (Exception telegramApiException) {
            log.error("Failed to send document response message to user", telegramApiException);
        }
    }

    private boolean isExcelDocument(Document document) {
        if (document == null || document.getFileId() == null || document.getFileId().isBlank()) {
            return false;
        }

        String fileName = document.getFileName() == null ? "" : document.getFileName().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls") || fileName.endsWith(".xlsm")) {
            return true;
        }

        String mimeType = document.getMimeType() == null ? "" : document.getMimeType().toLowerCase(Locale.ROOT);
        return mimeType.equals("application/vnd.ms-excel")
                || mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || mimeType.equals("application/vnd.ms-excel.sheet.macroenabled.12");
    }

    private String documentFileName(Document document) {
        if (document == null || document.getFileName() == null || document.getFileName().isBlank()) {
            return "Excel file";
        }
        return document.getFileName();
    }

    private void handlePlainTextMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        var language = BotText.language(languageService, userId);
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
            if (result.dayCompleted()) {
                ProgramService.ActiveTrainingDayProgression progression = programService.advanceActiveTrainingDayForUser(userId);
                response.setText(WorkoutMessageFormatter.formatFinishScreen(result.message(), progression, language));
                response.setParseMode("HTML");
                if (progression != null && progression.trainingDay() != null) {
                    response.setReplyMarkup(WorkoutMessageFormatter.startDayKeyboard(language));
                }
            } else if (!result.accepted()) {
                response.setText(localizeWorkoutServiceMessage(result.message(), language));
            } else {
                response.setText(WorkoutMessageFormatter.formatExerciseResult(result.message(), result.exerciseView(), language));
                response.setParseMode("HTML");
                response.setReplyMarkup(WorkoutMessageFormatter.exerciseKeyboard(result.exerciseView(), language));
            }

            sendTelegramMessage(response);
        } catch (WorkoutException e) {
            response.setText(localizeWorkoutServiceMessage(e.getMessage(), language));
            try {
                sendTelegramMessage(response);
            } catch (Exception telegramApiException) {
                log.error("Failed to send workout error message to user", telegramApiException);
            }
        } catch (Exception e) {
            log.error("Failed to handle workout input for user {}", userId, e);
            response.setText(BotText.workoutSaveError(language));
            try {
                sendTelegramMessage(response);
            } catch (Exception telegramApiException) {
                log.error("Failed to send workout error message to user", telegramApiException);
            }
        }
    }

    private String localizeWorkoutServiceMessage(String message, com.example.fitnessbot.model.UserLanguage language) {
        if ("No previous load is available for this exercise.".equals(message)) {
            return BotText.workoutNoPreviousLoad(language);
        }
        if ("You don't have an active workout session.".equals(message)) {
            return BotText.workoutNoActiveSession(language);
        }
        if ("You don't have an active training day.".equals(message)) {
            return BotText.workoutNoActiveTrainingDay(language);
        }
        if ("Active training day has no exercises.".equals(message)) {
            return BotText.workoutTrainingDayNoExercises(language);
        }
        if ("Current workout exercise is missing.".equals(message)) {
            return BotText.workoutCurrentExerciseMissing(language);
        }
        if (message != null && message.startsWith("👉 Send load for this set")) {
            return BotText.workoutLoadPrompt(BotText.workoutStepLabel(false, false, language), 1, language);
        }
        return message;
    }

    private SendMessage buildRenameProgramPrompt(CallbackQuery callbackQuery) {
        SendMessage response = new SendMessage();
        response.setChatId(callbackQuery.getMessage().getChatId().toString());
        var language = BotText.language(languageService, callbackQuery.getFrom().getId());

        Long programId;
        try {
            programId = Long.parseLong(callbackQuery.getData().substring("rename_program:".length()));
        } catch (NumberFormatException e) {
            response.setText(BotText.invalidProgramId(language));
            return response;
        }

        var program = programService.getProgramForUser(programId, callbackQuery.getFrom().getId());
        if (program.isEmpty()) {
            response.setText(BotText.programNotFound(language));
            return response;
        }

        renameSessionManager.startRename(callbackQuery.getFrom().getId(), programId);
        response.setText(BotText.renameProgramPrompt(program.get().getName(), language));
        return response;
    }

    private void handleProgramRenameMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        var language = BotText.language(languageService, userId);
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
            response.setText(BotText.programRenamed(program.getName(), program.getId(), language));
        } catch (ProgramException e) {
            if ("Program name can't be empty.".equals(e.getMessage())) {
                response.setText(BotText.emptyProgramName(language));
            } else {
                renameSessionManager.endRename(userId);
                if ("Program not found.".equals(e.getMessage())) {
                    response.setText(BotText.programNotFound(language));
                } else {
                    response.setText(e.getMessage());
                }
            }
        } catch (Exception e) {
            renameSessionManager.endRename(userId);
            log.error("Failed to rename program for user {}", userId, e);
            response.setText(BotText.renameProgramError(language));
        }

        try {
            sendTelegramMessage(response);
        } catch (Exception telegramApiException) {
            log.error("Failed to send rename program message to user", telegramApiException);
        }
    }

    private void handleCommand(Update update) {
        String command = update.getMessage().getText();
        Long userId = update.getMessage().getFrom().getId();
        var language = BotText.language(languageService, userId);

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
                response.setText(BotText.unknownCommand(language));
            }

            if (response != null) {
                sendTelegramMessage(response);
            }
        } catch (Exception e) {
            log.error("Failed to handle command: {}", command, e);

            try {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(update.getMessage().getChatId().toString());
                errorMessage.setText(BotText.commandProcessingError(language));
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
        var language = BotText.language(languageService, userId);
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId().toString());
        message.setText(BotText.chooseCommand(language));

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
        var language = BotText.language(languageService, userId);
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
            message.setText(BotText.commandSuggestions(language));

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
            message.setText(BotText.unknownCommand(language));

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
        return createTelegramCommandMenu(UserLanguage.ENGLISH);
    }

    List<BotCommand> createTelegramCommandMenu(UserLanguage language) {
        return List.of(
                new BotCommand("start", BotText.commandDescription("start", language)),
                new BotCommand("help", BotText.commandDescription("help", language)),
                new BotCommand("menu", BotText.commandDescription("menu", language)),
                new BotCommand("language", BotText.commandDescription("language", language)),
                new BotCommand("create_program", BotText.commandDescription("create_program", language)),
                new BotCommand("show_program", BotText.commandDescription("show_program", language)),
                new BotCommand("active_day", BotText.commandDescription("active_day", language))
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
