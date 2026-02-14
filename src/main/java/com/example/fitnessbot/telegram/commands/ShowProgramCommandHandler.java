package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/**
 * Handler for the /show_program command
 */
@Component
public class ShowProgramCommandHandler implements ContextAwareCommandHandler {

    public static final String COMMAND = "/show_program";

    private final ProgramService programService;
    private final ProgramCreationSessionManager sessionManager;

    public ShowProgramCommandHandler(ProgramService programService, ProgramCreationSessionManager sessionManager) {
        this.programService = programService;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Override
    public boolean isAvailable(Long userId, ProgramCreationSessionManager sessionManager) {
        // Show program is available if user has an active session
        return sessionManager.hasActiveSession(userId);
    }

    @Override
    public SendMessage handleUnavailable(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        sendMessage.setText("You don't have an active program creation session. Start one with /create_program <name>");
        return sendMessage;
    }

    @Transactional(readOnly = true)
    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());

        Long telegramUserId = update.getMessage().getFrom().getId();
        
        StringBuilder response = new StringBuilder();

        // Check if user has an active session
        var session = sessionManager.getSession(telegramUserId);
        if (session != null) {
            Program program = session.getProgram();
            response.append("*Program Creation Session: ").append(program.getName()).append("*\n\n");
            
            // Get training days from the session
            List<TrainingDay> trainingDays = session.getTrainingDays();
            if (!trainingDays.isEmpty()) {
                response.append("Training Days Added:\n");
                
                for (TrainingDay trainingDay : trainingDays) {
                    response.append("- ").append(trainingDay.getTitle()).append("\n");
                }
                
                response.append("\nTotal: ").append(trainingDays.size()).append(" training days");
            } else {
                response.append("No training days added yet.\n");
                response.append("Forward training day messages to add them to this program.");
            }
            
            // Set markdown for formatted response
            sendMessage.setParseMode("Markdown");
        } else {
            response.append("You don't have an active program creation session. Start one with /create_program <name>");
            // No markdown for plain text response
        }

        sendMessage.setText(response.toString());
        return sendMessage;
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Show details of the current program being created";
    }
}