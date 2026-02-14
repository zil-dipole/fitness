package com.example.fitnessbot.telegram.commands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/**
 * Handler for the /help command
 */
@Component
public class HelpCommandHandler implements CommandHandler {

    public static final String COMMAND = "/help";
    private final CommandRegistryService commandRegistryService;

    @Autowired
    public HelpCommandHandler(CommandRegistryService commandRegistryService) {
        this.commandRegistryService = commandRegistryService;
    }

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        
        // Build help text using command registry
        StringBuilder helpText = new StringBuilder();
        helpText.append("Simply forward your workout program messages to me and I'll parse and save them.\n\n");
        helpText.append("Supported format:\n");
        helpText.append("- Section headers ending with ':'\n");
        helpText.append("- Exercises with bullet points ('⁃' or '-')\n");
        helpText.append("- Sets and reps like \"3 x 10\"\n");
        helpText.append("- Video links\n\n");
        
        helpText.append("Available Commands:\n");
        List<CommandMetadata> commands = commandRegistryService.getAllCommands();
        for (CommandMetadata cmd : commands) {
            helpText.append(String.format("- %s - %s\n", cmd.getCommand(), cmd.getDescription()));
            if (cmd.getUsageExample() != null && !cmd.getUsageExample().isEmpty() && 
                !cmd.getUsageExample().equals(cmd.getCommand())) {
                helpText.append(String.format("  Example: %s\n", cmd.getUsageExample()));
            }
        }
        
        helpText.append("\nTip: Type \"/\" to see all available commands or start typing a command for suggestions!\n\n");
        
        helpText.append("Example program format:\n");
        helpText.append("Upper Body:\n");
        helpText.append("- Bench Press 3 x 10 (Warm up set)\n");
        helpText.append("- https://youtube.com/watch?v=example");
        
        sendMessage.setText(helpText.toString());
        return sendMessage;
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Show help";
    }
}