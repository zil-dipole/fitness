package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.FitnessBotException;
import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.exception.TrainingDayException;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
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

    public FinishProgramCommandHandler(ProgramService programService,
                                      ProgramCreationSessionManager sessionManager,
                                      MenuKeyboardFactory menuKeyboardFactory) {
        this.programService = programService;
        this.sessionManager = sessionManager;
        this.menuKeyboardFactory = menuKeyboardFactory;
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
        response.setText("There isn't a program draft in progress.\n\nStart one with /create_program <program_name>.");
        return response;
    }

    @Override
    public SendMessage handle(Update update) {
        Long userId = update.getMessage().getFrom().getId();

        // Check if user has an active session
        if (!sessionManager.hasActiveSession(userId)) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText("There isn't a program draft in progress.\n\nStart one with /create_program <program_name>.");
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
                response.setText("⚠️ Your program draft is empty.\n\nForward at least one training day before finishing.");
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
            response.setText("✅ Program \"" + program.getName() + "\" is ready.\n" +
                    "Added " + trainingDays.size() + " training " + (trainingDays.size() == 1 ? "day" : "days") + ".\n\n" +
                    "Use /show_program to open it.");
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
            response.setText("❌ Sorry, there was an error finishing program creation. Please try again.");
            return response;
        }
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Finish creating current program";
    }
}
