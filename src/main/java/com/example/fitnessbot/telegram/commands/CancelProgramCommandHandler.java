package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for the /cancel_program command
 */
@Component
public class CancelProgramCommandHandler implements CommandHandler {
    
    private final ProgramCreationSessionManager sessionManager;
    
    public CancelProgramCommandHandler(ProgramCreationSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    @Override
    public boolean canHandle(String command) {
        return "/cancel_program".equals(command);
    }
    
    @Override
    public SendMessage handle(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        
        // Check if user has an active session
        if (!sessionManager.hasActiveSession(userId)) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText("You don't have an active program creation session to cancel.");
            return response;
        }
        
        // End the session
        sessionManager.endSession(userId);
        
        SendMessage response = new SendMessage();
        response.setChatId(update.getMessage().getChatId().toString());
        response.setText("✅ Program creation cancelled.");
        return response;
    }
}