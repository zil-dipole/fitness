package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Comparator;
import java.util.List;

/**
 * Handler for the /help command
 */
@Component
public class HelpCommandHandler implements CommandHandler {

    public static final String COMMAND = "/help";
    private final CommandRegistryService commandRegistryService;
    private final MenuKeyboardFactory menuKeyboardFactory;

    @Autowired
    public HelpCommandHandler(CommandRegistryService commandRegistryService,
                             MenuKeyboardFactory menuKeyboardFactory) {
        this.commandRegistryService = commandRegistryService;
        this.menuKeyboardFactory = menuKeyboardFactory;
    }

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());

        StringBuilder helpText = new StringBuilder();
        helpText.append("<b>How it works</b>\n");
        helpText.append("1. Start a program with <code>/create_program &lt;name&gt;</code> or use the menu.\n");
        helpText.append("2. Forward each training day message you want to save.\n");
        helpText.append("3. Finish the program, then open it any time from <code>/show_program</code>.\n\n");

        helpText.append("<b>Message format I can read</b>\n");
        helpText.append("• Section headers ending with <code>:</code>\n");
        helpText.append("• Exercise lines starting with <code>-</code> or <code>⁃</code>\n");
        helpText.append("• Sets and reps like <code>3 x 10</code>\n");
        helpText.append("• Video links on separate lines\n\n");

        helpText.append("<b>Commands</b>\n");
        List<CommandMetadata> commands = commandRegistryService.getAllCommands().stream()
                .sorted(Comparator.comparing(CommandMetadata::getCommand))
                .toList();
        for (CommandMetadata cmd : commands) {
            helpText.append("• <b>")
                    .append(TrainingDayMessageFormatter.escapeHtml(cmd.getCommand()))
                    .append("</b> - ")
                    .append(TrainingDayMessageFormatter.escapeHtml(cmd.getDescription()))
                    .append("\n");
            if (cmd.getUsageExample() != null && !cmd.getUsageExample().isEmpty() &&
                !cmd.getUsageExample().equals(cmd.getCommand())) {
                helpText.append("  Example: <code>")
                        .append(TrainingDayMessageFormatter.escapeHtml(cmd.getUsageExample()))
                        .append("</code>\n");
            }
        }

        helpText.append("\n<b>Example message</b>\n");
        helpText.append("<pre>Upper Body:\n");
        helpText.append("- Bench Press 3 x 10\n");
        helpText.append("- Pull-Ups 3 x 8\n");
        helpText.append("- https://youtube.com/watch?v=example</pre>\n");
        helpText.append("Type <code>/</code> to see all available commands, or use <code>/menu</code> to return to the main actions.");

        sendMessage.setText(helpText.toString());
        sendMessage.setParseMode("HTML");
        sendMessage.setReplyMarkup(menuKeyboardFactory.createMainMenuKeyboard(update.getMessage().getFrom().getId()));
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
