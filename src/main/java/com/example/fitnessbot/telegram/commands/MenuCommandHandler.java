package com.example.fitnessbot.telegram.commands;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for the /menu command that displays the main menu with buttons
 */
@Component
public class MenuCommandHandler implements CommandHandler {

    public static final String COMMAND = "/menu";

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        sendMessage.setText("Welcome to Fitness Bot! Choose an option below:");
        
        // Create inline keyboard markup
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // First row - Create Program and View Programs
        List<InlineKeyboardButton> firstRow = new ArrayList<>();
        InlineKeyboardButton createProgramBtn = new InlineKeyboardButton();
        createProgramBtn.setText("Create Program");
        createProgramBtn.setCallbackData("create_program");
        firstRow.add(createProgramBtn);
        
        InlineKeyboardButton viewProgramsBtn = new InlineKeyboardButton();
        viewProgramsBtn.setText("View Programs");
        viewProgramsBtn.setCallbackData("view_programs");
        firstRow.add(viewProgramsBtn);
        
        rows.add(firstRow);
        
        // Second row - Help
        List<InlineKeyboardButton> secondRow = new ArrayList<>();
        InlineKeyboardButton helpBtn = new InlineKeyboardButton();
        helpBtn.setText("Help");
        helpBtn.setCallbackData("help");
        secondRow.add(helpBtn);
        
        rows.add(secondRow);
        
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
        return "Show menu";
    }
}