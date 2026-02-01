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
        sendMessage.setText("Simply forward your workout program messages to me and I'll parse and save them.\n\n" +
                          "Supported format:\n" +
                          "- Section headers ending with ':'\n" +
                          "- Exercises with bullet points ('⁃' or '-')\n" +
                          "- Sets and reps like \"3 x 10\"\n" +
                          "- Video links\n\n" +
                          "Example:\n" +
                          "Upper Body:\n" +
                          "- Bench Press 3 x 10 (Warm up set)\n" +
                          "- https://youtube.com/watch?v=example");
        return sendMessage;
    }
}