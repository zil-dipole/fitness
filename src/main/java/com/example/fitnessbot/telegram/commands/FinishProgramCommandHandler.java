package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.FitnessBotException;
import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.exception.TrainingDayException;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for the /finish_program command
 */
@Component
public class FinishProgramCommandHandler implements ContextAwareCommandHandler {

    public static final String COMMAND = "/finish_program";
    private final ProgramService programService;
    private final ProgramCreationSessionManager sessionManager;
    private final MenuKeyboardFactory menuKeyboardFactory;
    private final UserLanguageService languageService;

    @Autowired
    public FinishProgramCommandHandler(ProgramService programService,
                                       ProgramCreationSessionManager sessionManager,
                                       MenuKeyboardFactory menuKeyboardFactory,
                                       UserLanguageService languageService) {
        this.programService = programService;
        this.sessionManager = sessionManager;
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = languageService;
    }

    public FinishProgramCommandHandler(ProgramService programService,
                                      ProgramCreationSessionManager sessionManager,
                                      MenuKeyboardFactory menuKeyboardFactory) {
        this.programService = programService;
        this.sessionManager = sessionManager;
        this.menuKeyboardFactory = menuKeyboardFactory;
        this.languageService = null;
    }

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Override
    public boolean isAvailable(Long userId, ProgramCreationSessionManager sessionManager) {
        return sessionManager.hasActiveSession(userId);
    }

    @Override
    public SendMessage handleUnavailable(Update update) {
        SendMessage response = new SendMessage();
        response.setChatId(update.getMessage().getChatId().toString());
        var language = BotText.language(languageService, update.getMessage().getFrom().getId());
        response.setText(BotText.finishProgramNoSession(language));
        return response;
    }

    @Override
    public SendMessage handle(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        var language = BotText.language(languageService, userId);

        // Check if user has an active session
        if (!sessionManager.hasActiveSession(userId)) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText(BotText.finishProgramNoSession(language));
            return response;
        }

        try {
            // Get the session
            var session = sessionManager.getSession(userId);
            var program = session.getProgram();
            var trainingDays = session.getTrainingDays();

            // Check if any training days were added
            if (trainingDays.isEmpty()) {
                SendMessage response = new SendMessage();
                response.setChatId(update.getMessage().getChatId().toString());
                response.setText(BotText.finishProgramEmpty(language));
                return response;
            }

            // Add all training days to the program
            int position = 1;
            for (TrainingDay trainingDay : trainingDays) {
                programService.addTrainingDayToProgram(program.getId(), trainingDay.getId(), position++);
            }

            // End the session
            sessionManager.endSession(userId);

            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText(BotText.finishProgramSuccess(program.getName(), trainingDays.size(), language));
            response.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(userId));
            return response;
        } catch (ProgramException | TrainingDayException e) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText("❌ " + e.getMessage());
            return response;
        } catch (Exception e) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText(BotText.finishProgramGenericError(language));
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
