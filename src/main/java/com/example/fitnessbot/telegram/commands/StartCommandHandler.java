package com.example.fitnessbot.telegram.commands;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for the /start command
 */
@Component
public class StartCommandHandler implements CommandHandler {

    public static final String COMMAND = "/start";

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        sendMessage.setText("Welcome to Fitness Bot! Forward your workout programs to me and I'll parse and save them for you.\n\nClick the button below to open the main menu:");
        
        // Create inline keyboard with menu button
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton menuButton = new InlineKeyboardButton();
        menuButton.setText("Open Menu");
        menuButton.setCallbackData("start_menu");
        row.add(menuButton);
        
        rows.add(row);
        markup.setKeyboard(rows);
        sendMessage.setReplyMarkup(markup);
        
        return sendMessage;
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Start bot";
    }
}