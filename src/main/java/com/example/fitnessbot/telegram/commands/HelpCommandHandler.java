package com.example.fitnessbot.telegram.commands;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for the /help command
 */
@Component
public class HelpCommandHandler implements CommandHandler {

    @Override
    public boolean canHandle(String command) {
        return "/help".equals(command);
    }

    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        sendMessage.setText("""
                Simply forward your workout program messages to me and I'll parse and save them.
                
                Supported format:
                - Section headers ending with ':'
                - Exercises with bullet points ('⁃' or '-')
                - Sets and reps like "3 x 10"
                - Video links
                
                Program Creation Commands:
                - /create_program <name> - Start creating a new program
                - Forward training day messages to add them to the program
                - /finish_program - Finish and save the program
                - /cancel_program - Cancel program creation
                
                Example:
                Upper Body:
                - Bench Press 3 x 10 (Warm up set)
                - https://youtube.com/watch?v=example""");
        return sendMessage;
    }
}