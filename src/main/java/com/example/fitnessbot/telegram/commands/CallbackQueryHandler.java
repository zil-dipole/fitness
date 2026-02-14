package com.example.fitnessbot.telegram.commands;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Interface for handling callback queries from inline keyboards
 */
public interface CallbackQueryHandler {
    /**
     * Check if this handler can handle the given callback query
     * @param callbackQuery The callback query to check
     * @return true if this handler can handle the callback query, false otherwise
     */
    boolean canHandle(CallbackQuery callbackQuery);

    /**
     * Handle the callback query
     * @param update The update containing the callback query
     * @return The response message
     */
    SendMessage handle(Update update);
}