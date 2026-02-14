package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for the /show_program command
 */
@Component
public class ShowProgramCommandHandler implements CommandHandler {

    public static final String COMMAND = "/show_program";

    private final ProgramService programService;
    private final ProgramCreationSessionManager sessionManager;

    public ShowProgramCommandHandler(ProgramService programService,
                                   ProgramCreationSessionManager sessionManager) {
        this.programService = programService;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Transactional(readOnly = true)
    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());

        Long telegramUserId = update.getMessage().getFrom().getId();

        // Check if user has an active program
        Program activeProgram = programService.getActiveProgram(telegramUserId);
        
        if (activeProgram != null) {
            StringBuilder response = new StringBuilder();
            response.append("*Active Program: ").append(activeProgram.getName()).append("*\n\n");

            // Get training days for the program
            if (activeProgram.getProgramTrainingDays() != null && !activeProgram.getProgramTrainingDays().isEmpty()) {
                response.append("Training Days:\n");
                activeProgram.getProgramTrainingDays().forEach(ptd -> 
                    response.append("- ").append(ptd.getTrainingDay().getTitle()).append("\n"));
            } else {
                response.append("No training days added yet.\n");
            }

            sendMessage.setText(response.toString());
            sendMessage.setParseMode("Markdown");
        } else {
            sendMessage.setText("You don't have an active program. Start one with /create_program <name>");
        }

        return sendMessage;
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Show details of the current active program";
    }
}