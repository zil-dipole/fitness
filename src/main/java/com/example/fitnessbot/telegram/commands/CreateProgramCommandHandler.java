package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.FitnessBotException;
import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
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

    public CreateProgramCommandHandler(ProgramService programService,
                                      ProgramCreationSessionManager sessionManager,
                                      MenuKeyboardFactory menuKeyboardFactory) {
        this.programService = programService;
        this.sessionManager = sessionManager;
        this.menuKeyboardFactory = menuKeyboardFactory;
    }

    @Override
    public boolean canHandle(String command) {
        return command.startsWith(COMMAND);
    }

    @Override
    public boolean isAvailable(Long userId, ProgramCreationSessionManager sessionManager) {
        // Create program is available only if user doesn't have an active session
        return !sessionManager.hasActiveSession(userId);
    }

    @Override
    public SendMessage handleUnavailable(Update update) {
        SendMessage response = new SendMessage();
        response.setChatId(update.getMessage().getChatId().toString());
        response.setText("""
                You already have a program draft in progress.

                Finish it with /finish_program or cancel it with /cancel_program.
                """);
        return response;
    }

    @Override
    public SendMessage handle(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();

        // Check if user already has an active session
        if (sessionManager.hasActiveSession(userId)) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText("""
                    You already have a program draft in progress.

                    Finish it with /finish_program or cancel it with /cancel_program.
                    """);
            return response;
        }

        // Extract program name from command
        String programName = "My Program";
        String[] parts = messageText.split(" ", 2);
        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
            programName = parts[1].trim();
        }

        try {
            // Create a new program
            Program program = programService.startProgramCreation(userId, programName);

            // Start a session for this user
            sessionManager.startSession(userId, program);

            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText("✅ Program draft created: \"" + program.getName() + "\"\n\n" +
                    "Send or forward the training day messages you want to include.\n" +
                    "When you're done, tap \"Finish Program\" or send /finish_program.");
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
            response.setText("❌ Sorry, there was an error starting program creation. Please try again.");
            return response;
        }
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Create training program";
    }
}
