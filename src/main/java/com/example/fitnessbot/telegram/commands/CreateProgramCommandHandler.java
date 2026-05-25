package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.FitnessBotException;
import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for the /create_program command
 */
@Component
public class CreateProgramCommandHandler implements ContextAwareCommandHandler {

    public static final String COMMAND = "/create_program";
    private final ProgramService programService;
    private final ProgramCreationSessionManager sessionManager;
    private final MenuKeyboardFactory menuKeyboardFactory;
    private final UserLanguageService languageService;

    @Autowired
    public CreateProgramCommandHandler(ProgramService programService,
                                       ProgramCreationSessionManager sessionManager,
                                       MenuKeyboardFactory menuKeyboardFactory,
                                       UserLanguageService languageService) {
        this.programService = programService;
        this.sessionManager = sessionManager;
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = languageService;
    }

    public CreateProgramCommandHandler(ProgramService programService,
                                      ProgramCreationSessionManager sessionManager,
                                      MenuKeyboardFactory menuKeyboardFactory) {
        this.programService = programService;
        this.sessionManager = sessionManager;
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = null;
    }

    @Override
    public boolean canHandle(String command) {
        return command.startsWith(COMMAND);
    }

    @Override
    public boolean isAvailable(Long userId, ProgramCreationSessionManager sessionManager) {
        // Create program is available only when no program creation flow is already in progress.
        return !sessionManager.hasProgramCreationInProgress(userId);
    }

    @Override
    public SendMessage handleUnavailable(Update update) {
        SendMessage response = new SendMessage();
        response.setChatId(update.getMessage().getChatId().toString());
        Long userId = update.getMessage().getFrom().getId();
        var language = BotText.language(languageService, userId);
        if (sessionManager.isAwaitingProgramName(userId)) {
            response.setText(BotText.createProgramNamePrompt(language));
        } else {
            response.setText(BotText.createProgramUnavailable(language));
        }
        return response;
    }

    @Override
    public SendMessage handle(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();
        var language = BotText.language(languageService, userId);

        if (sessionManager.hasProgramCreationInProgress(userId)) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            if (sessionManager.isAwaitingProgramName(userId)) {
                response.setText(BotText.createProgramNamePrompt(language));
            } else {
                response.setText(BotText.createProgramUnavailable(language));
            }
            return response;
        }

        String[] parts = messageText.split(" ", 2);
        if (parts.length == 1 || parts[1].trim().isEmpty()) {
            sessionManager.startAwaitingProgramName(userId);

            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText(BotText.createProgramNamePrompt(language));
            response.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(userId));
            return response;
        }

        String programName = parts[1].trim();

        try {
            // Create a new program
            Program program = programService.startProgramCreation(userId, programName);

            // Start a session for this user
            sessionManager.startSession(userId, program);

            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText(BotText.programDraftCreated(program.getName(), language));
            response.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(userId));
            return response;
        } catch (ProgramException e) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText("❌ " + e.getMessage());
            return response;
        } catch (Exception e) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText(BotText.createProgramGenericError(language));
            return response;
        }
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return BotText.commandDescription(COMMAND, null);
    }
}
