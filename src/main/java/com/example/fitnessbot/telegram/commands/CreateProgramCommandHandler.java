package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for the /create_program command
 */
@Component
public class CreateProgramCommandHandler implements CommandHandler {

    public static final String COMMAND = "/create_program";
    private final ProgramService programService;
    private final ProgramCreationSessionManager sessionManager;
    
    public CreateProgramCommandHandler(ProgramService programService, 
                                      ProgramCreationSessionManager sessionManager) {
        this.programService = programService;
        this.sessionManager = sessionManager;
    }
    
    @Override
    public boolean canHandle(String command) {
        return command.startsWith(COMMAND);
    }
    
    @Override
    public SendMessage handle(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();
        
        // Check if user already has an active session
        if (sessionManager.hasActiveSession(userId)) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText("You already have an active program creation session. Please finish it first with /finish_program or cancel it with /cancel_program.");
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
            response.setText("✅ Started creating program: \"" + programName + "\"\n\n" +
                          "Now forward the training day messages you want to include in this program.\n" +
                          "When you're done, send /finish_program to complete the process.");
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