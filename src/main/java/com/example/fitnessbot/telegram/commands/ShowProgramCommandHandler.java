package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.TrainingDay;
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
import java.util.Optional;

/**
 * Handler for the /show_program command
 */
@Component
public class ShowProgramCommandHandler implements ContextAwareCommandHandler {

    public static final String COMMAND = "/show_program";

    private final ProgramService programService;
    private final ProgramCreationSessionManager sessionManager;

    public ShowProgramCommandHandler(ProgramService programService, ProgramCreationSessionManager sessionManager) {
        this.programService = programService;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean canHandle(String command) {
        return command != null && (COMMAND.equals(command) || command.startsWith(COMMAND + " "));
    }

    @Override
    public boolean isAvailable(Long userId, ProgramCreationSessionManager sessionManager) {
        return true;
    }

    @Override
    public SendMessage handleUnavailable(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        sendMessage.setText("You don't have an active program creation session. Start one with /create_program <name>");
        return sendMessage;
    }

    @Transactional(readOnly = true)
    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());

        Long telegramUserId = update.getMessage().getFrom().getId();
        String selector = extractSelector(update.getMessage().getText());
        Long requestedProgramId = extractProgramId(selector);
        if (requestedProgramId != null) {
            return buildSavedProgramDetails(sendMessage, telegramUserId, requestedProgramId);
        }
        if (hasInvalidProgramId(selector)) {
            sendMessage.setText("Invalid program ID. Use /show_program <program_id>.");
            return sendMessage;
        }
        if (selector != null) {
            return buildSavedProgramDetailsByName(sendMessage, telegramUserId, selector);
        }

        StringBuilder response = new StringBuilder();

        // Check if user has an active session
        var session = sessionManager.getSession(telegramUserId);
        if (session != null) {
            Program program = session.getProgram();
            response.append("<b>Program Creation Session: ")
                    .append(TrainingDayMessageFormatter.escapeHtml(program.getName()))
                    .append("</b>\n\n");
            
            // Get training days from the session
            List<TrainingDay> trainingDays = session.getTrainingDays();
            if (!trainingDays.isEmpty()) {
                response.append("Training Days Added:\n");
                
                for (TrainingDay trainingDay : trainingDays) {
                    response.append("- ")
                            .append(TrainingDayMessageFormatter.escapeHtml(trainingDay.getTitle()))
                            .append("\n");
                }
                
                response.append("\nTotal: ").append(trainingDays.size()).append(" training days");
            } else {
                response.append("No training days added yet.\n");
                response.append("Forward training day messages to add them to this program.");
            }
            
            sendMessage.setParseMode("HTML");
        } else {
            return buildSavedProgramList(sendMessage, telegramUserId);
        }

        sendMessage.setText(response.toString());
        return sendMessage;
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Show current or saved programs";
    }

    private SendMessage buildSavedProgramList(SendMessage sendMessage, Long telegramUserId) {
        List<Program> programs = programService.getProgramsForUser(telegramUserId);
        if (programs.isEmpty()) {
            sendMessage.setText("You don't have any saved programs yet. Create one with /create_program <name>.");
            return sendMessage;
        }

        StringBuilder response = new StringBuilder("Your saved programs:\n\n");
        for (Program program : programs) {
            response.append("- #")
                    .append(program.getId())
                    .append(" ")
                    .append(program.getName())
                    .append("\n");
        }
        response.append("\nUse the number after # to open a program, for example: /show_program ")
                .append(programs.get(0).getId());

        sendMessage.setText(response.toString());
        sendMessage.setReplyMarkup(createProgramButtons(programs));
        return sendMessage;
    }

    private SendMessage buildSavedProgramDetails(SendMessage sendMessage, Long telegramUserId, Long programId) {
        Optional<Program> program = programService.getProgramForUser(programId, telegramUserId);
        if (program.isEmpty()) {
            sendMessage.setText("Program not found.");
            return sendMessage;
        }

        List<com.example.fitnessbot.model.ProgramTrainingDay> trainingDays =
                programService.getProgramTrainingDaysForUser(programId, telegramUserId);

        StringBuilder response = new StringBuilder();
        response.append("Program: ").append(program.get().getName()).append("\n\n");

        if (trainingDays.isEmpty()) {
            response.append("No training days are linked to this program yet.");
        } else {
            response.append("Training days:\n");
            for (com.example.fitnessbot.model.ProgramTrainingDay programTrainingDay : trainingDays) {
                TrainingDay trainingDay = programTrainingDay.getTrainingDay();
                response.append(programTrainingDay.getPosition())
                        .append(". ")
                        .append(trainingDay.getTitle())
                        .append("\n");
            }
            response.append("\nTotal: ").append(trainingDays.size()).append(" training days");
        }

        sendMessage.setText(response.toString());
        sendMessage.setReplyMarkup(createProgramDetailsButtons(programId, trainingDays));
        return sendMessage;
    }

    private SendMessage buildSavedProgramDetailsByName(SendMessage sendMessage, Long telegramUserId, String programName) {
        List<Program> matches = programService.getProgramsForUser(telegramUserId).stream()
                .filter(program -> program.getName() != null && program.getName().equalsIgnoreCase(programName.trim()))
                .toList();

        if (matches.isEmpty()) {
            sendMessage.setText("Program not found. Send /show_program to see your saved programs.");
            return sendMessage;
        }
        if (matches.size() > 1) {
            sendMessage.setText("Multiple programs named \"" + programName + "\" found. Choose one:");
            sendMessage.setReplyMarkup(createProgramButtons(matches));
            return sendMessage;
        }

        return buildSavedProgramDetails(sendMessage, telegramUserId, matches.getFirst().getId());
    }

    private InlineKeyboardMarkup createProgramButtons(List<Program> programs) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Program program : programs) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("#" + program.getId() + " " + program.getName());
            button.setCallbackData("show_program:" + program.getId());
            rows.add(List.of(button));
        }

        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createProgramDetailsButtons(
            Long programId,
            List<com.example.fitnessbot.model.ProgramTrainingDay> trainingDays) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton startButton = new InlineKeyboardButton();
        startButton.setText("Start Program");
        startButton.setCallbackData("start_program:" + programId);

        InlineKeyboardButton deleteButton = new InlineKeyboardButton();
        deleteButton.setText("Delete Program");
        deleteButton.setCallbackData("delete_program:" + programId);
        rows.add(List.of(startButton, deleteButton));

        for (com.example.fitnessbot.model.ProgramTrainingDay programTrainingDay : trainingDays) {
            TrainingDay trainingDay = programTrainingDay.getTrainingDay();
            InlineKeyboardButton dayButton = new InlineKeyboardButton();
            dayButton.setText("Day " + programTrainingDay.getPosition() + ": " + trainingDay.getTitle());
            dayButton.setCallbackData("show_day_" + trainingDay.getId());
            rows.add(List.of(dayButton));
        }

        markup.setKeyboard(rows);
        return markup;
    }

    private String extractSelector(String command) {
        if (command == null) {
            return null;
        }

        String[] parts = command.trim().split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            return null;
        }
        return parts[1].trim();
    }

    private Long extractProgramId(String selector) {
        if (selector == null) {
            return null;
        }

        String trimmedSelector = selector.trim();
        String firstToken = trimmedSelector.split("\\s+", 2)[0];
        String idCandidate;
        if (firstToken.startsWith("#")) {
            idCandidate = firstToken.substring(1);
        } else if (trimmedSelector.matches("\\d+")) {
            idCandidate = trimmedSelector;
        } else {
            return null;
        }

        try {
            return Long.parseLong(idCandidate);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean hasInvalidProgramId(String selector) {
        if (selector == null) {
            return false;
        }

        String trimmedSelector = selector.trim();
        if (!trimmedSelector.startsWith("#")) {
            return false;
        }

        String firstToken = trimmedSelector.split("\\s+", 2)[0];
        try {
            Long.parseLong(firstToken.substring(1));
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
