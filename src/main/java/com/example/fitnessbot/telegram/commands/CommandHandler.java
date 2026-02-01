package com.example.fitnessbot.telegram.commands;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Interface for handling Telegram bot commands
 */
public interface CommandHandler {
    /**
     * Check if this handler can handle the given command
     * @param command the command string (e.g., "/start")
     * @return true if this handler can process the command
     */
    boolean canHandle(String command);
    
    /**
     * Handle the command from the update
     * @param update the Telegram update object
     * @return SendMessage object with the response
     */
    SendMessage handle(Update update);
}