package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.ProgramTrainingDay;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for the /show_program command
 */
@Component
public class ShowProgramCommandHandler implements ContextAwareCommandHandler {

    public static final String COMMAND = "/show_program";

    private final ProgramService programService;

    public ShowProgramCommandHandler(ProgramService programService) {
        this.programService = programService;
    }

    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }

    @Override
    public boolean isAvailable(Long userId, ProgramCreationSessionManager sessionManager) {
        // Show program is available if user has an active program or active session
        return programService.getActiveProgram(userId) != null || sessionManager.hasActiveSession(userId);
    }

    @Override
    public SendMessage handleUnavailable(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        sendMessage.setText("You don't have an active program or program creation session. Start one with /create_program <name>");
        return sendMessage;
    }

    @Transactional(readOnly = true)
    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());

        Long telegramUserId = update.getMessage().getFrom().getId();

        // Check if user has an active program
        Program activeProgram = programService.getActiveProgram(telegramUserId);
        
        if (activeProgram != null) {
            StringBuilder response = new StringBuilder();
            response.append("*Active Program: ").append(activeProgram.getName()).append("*\n\n");

            // Get training days for the program
            if (activeProgram.getProgramTrainingDays() != null && !activeProgram.getProgramTrainingDays().isEmpty()) {
                response.append("Training Days:\n");
                
                // Create inline keyboard markup for training days
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                
                for (ProgramTrainingDay ptd : activeProgram.getProgramTrainingDays()) {
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    InlineKeyboardButton button = new InlineKeyboardButton();
                    button.setText(ptd.getTrainingDay().getTitle());
                    button.setCallbackData("show_day_" + ptd.getTrainingDay().getId());
                    row.add(button);
                    rows.add(row);
                    
                    response.append("- ").append(ptd.getTrainingDay().getTitle()).append("\n");
                }
                
                markup.setKeyboard(rows);
                sendMessage.setReplyMarkup(markup);
            } else {
                response.append("No training days added yet.\n");
            }

            sendMessage.setText(response.toString());
            sendMessage.setParseMode("Markdown");
        } else {
            sendMessage.setText("You don't have an active program. Start one with /create_program <name>");
        }

        return sendMessage;
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Show details of the current active program";
    }
}