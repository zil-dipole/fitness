package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Interface for command handlers that can make decisions based on user context
 */
public interface ContextAwareCommandHandler extends CommandHandler {
    
    /**
     * Check if this command is available in the current context
     * @param userId The user ID to check context for
     * @param sessionManager The session manager to check for active sessions
     * @return true if the command is available, false otherwise
     */
    boolean isAvailable(Long userId, ProgramCreationSessionManager sessionManager);
    
    /**
     * Handle the command when it's not available in the current context
     * @param update The update that triggered the command
     * @return The response message to send to the user
     */
    SendMessage handleUnavailable(Update update);
}