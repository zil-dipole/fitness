package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.UserLanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Handler for the /show_program command
 */
@Component
public class ShowProgramCommandHandler implements ContextAwareCommandHandler {

    public static final String COMMAND = "/show_program";
    private static final DateTimeFormatter PROGRAM_CREATED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProgramService programService;
    private final ProgramCreationSessionManager sessionManager;
    private final UserLanguageService languageService;

    @Autowired
    public ShowProgramCommandHandler(ProgramService programService,
                                     ProgramCreationSessionManager sessionManager,
                                     UserLanguageService languageService) {
        this.programService = programService;
        this.sessionManager = sessionManager;
        this.languageService = languageService;
    }

    public ShowProgramCommandHandler(ProgramService programService, ProgramCreationSessionManager sessionManager) {
        this.programService = programService;
        this.sessionManager = sessionManager;
        this.languageService = null;
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
        var language = BotText.language(languageService, update.getMessage().getFrom().getId());
        sendMessage.setText(BotText.showProgramUnavailable(language));
        return sendMessage;
    }

    @Transactional(readOnly = true)
    @Override
    public SendMessage handle(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());

        Long telegramUserId = update.getMessage().getFrom().getId();
        var language = BotText.language(languageService, telegramUserId);
        String selector = extractSelector(update.getMessage().getText());
        Long requestedProgramId = extractProgramId(selector);
        if (requestedProgramId != null) {
            return buildSavedProgramDetails(sendMessage, telegramUserId, requestedProgramId, language);
        }
        if (hasInvalidProgramId(selector)) {
            sendMessage.setText(BotText.invalidProgramId(language));
            return sendMessage;
        }
        if (selector != null) {
            return buildSavedProgramDetailsByName(sendMessage, telegramUserId, selector, language);
        }

        StringBuilder response = new StringBuilder();

        // Check if user has an active session
        var session = sessionManager.getSession(telegramUserId);
        if (session != null) {
            Program program = session.getProgram();
            response.append(BotText.programDraftTitle(program.getName(), language));

            List<TrainingDay> trainingDays = session.getTrainingDays();
            if (!trainingDays.isEmpty()) {
                response.append(BotText.trainingDaysAddedHeader(language));
                for (TrainingDay trainingDay : trainingDays) {
                    response.append("• ")
                            .append(TrainingDayMessageFormatter.escapeHtml(trainingDay.getTitle()))
                            .append("\n");
                }

                response.append(BotText.draftProgress(trainingDays.size(), language));
            } else {
                response.append(BotText.noTrainingDaysAdded(language));
            }

            sendMessage.setParseMode("HTML");
        } else {
            return buildSavedProgramList(sendMessage, telegramUserId, language);
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
        return BotText.commandDescription(COMMAND, null);
    }

    private SendMessage buildSavedProgramList(SendMessage sendMessage, Long telegramUserId, com.example.fitnessbot.model.UserLanguage language) {
        List<Program> programs = programService.getProgramsForUser(telegramUserId);
        if (programs.isEmpty()) {
            sendMessage.setText(BotText.noSavedPrograms(language));
            return sendMessage;
        }

        StringBuilder response = new StringBuilder(BotText.savedProgramsHeader(language));
        List<String> programLabels = buildProgramLabels(programs, language);
        for (String programLabel : programLabels) {
            response.append("• ")
                    .append(TrainingDayMessageFormatter.escapeHtml(programLabel))
                    .append("\n");
        }
        response.append(BotText.openProgramHint(getProgramDisplayName(programs.getFirst(), language), language));

        sendMessage.setText(response.toString());
        sendMessage.setParseMode("HTML");
        sendMessage.setReplyMarkup(createProgramButtons(programs, language));
        return sendMessage;
    }

    private SendMessage buildSavedProgramDetails(SendMessage sendMessage,
                                                 Long telegramUserId,
                                                 Long programId,
                                                 com.example.fitnessbot.model.UserLanguage language) {
        Optional<Program> program = programService.getProgramForUser(programId, telegramUserId);
        if (program.isEmpty()) {
            sendMessage.setText(BotText.programNotFound(language));
            return sendMessage;
        }

        List<com.example.fitnessbot.model.ProgramTrainingDay> trainingDays =
                programService.getProgramTrainingDaysForUser(programId, telegramUserId);

        StringBuilder response = new StringBuilder();
        response.append(BotText.programDetailsHeader(program.get().getName(), language));

        if (trainingDays.isEmpty()) {
            response.append(BotText.noLinkedTrainingDays(language));
        } else {
            response.append(BotText.trainingDaysHeader(language));
            for (com.example.fitnessbot.model.ProgramTrainingDay programTrainingDay : trainingDays) {
                TrainingDay trainingDay = programTrainingDay.getTrainingDay();
                response.append(programTrainingDay.getPosition())
                        .append(". ")
                        .append(TrainingDayMessageFormatter.escapeHtml(trainingDay.getTitle()))
                        .append("\n");
            }
            response.append("\n")
                    .append(BotText.trainingDaysTotal(trainingDays.size(), language));
            response.append(BotText.tapStartProgram(language));
        }

        sendMessage.setText(response.toString());
        sendMessage.setParseMode("HTML");
        sendMessage.setReplyMarkup(createProgramDetailsButtons(programId, trainingDays, language));
        return sendMessage;
    }

    private SendMessage buildSavedProgramDetailsByName(SendMessage sendMessage,
                                                       Long telegramUserId,
                                                       String programName,
                                                       com.example.fitnessbot.model.UserLanguage language) {
        List<Program> matches = programService.getProgramsForUser(telegramUserId).stream()
                .filter(program -> program.getName() != null && program.getName().equalsIgnoreCase(programName.trim()))
                .toList();

        if (matches.isEmpty()) {
            sendMessage.setText(BotText.programNotFoundShowList(language));
            return sendMessage;
        }
        if (matches.size() > 1) {
            sendMessage.setText(BotText.multipleProgramsFound(programName, language));
            sendMessage.setReplyMarkup(createProgramButtons(matches, language));
            return sendMessage;
        }

        return buildSavedProgramDetails(sendMessage, telegramUserId, matches.getFirst().getId(), language);
    }

    private InlineKeyboardMarkup createProgramButtons(List<Program> programs, com.example.fitnessbot.model.UserLanguage language) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<String> programLabels = buildProgramLabels(programs, language);

        for (int i = 0; i < programs.size(); i++) {
            Program program = programs.get(i);
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(programLabels.get(i));
            button.setCallbackData("show_program:" + program.getId());
            rows.add(List.of(button));
        }

        markup.setKeyboard(rows);
        return markup;
    }

    private List<String> buildProgramLabels(List<Program> programs, com.example.fitnessbot.model.UserLanguage language) {
        Map<String, Integer> nameCounts = new HashMap<>();
        for (Program program : programs) {
            nameCounts.merge(normalizeProgramName(program.getName()), 1, Integer::sum);
        }

        Map<String, Integer> seenCounts = new HashMap<>();
        List<String> labels = new ArrayList<>(programs.size());

        for (Program program : programs) {
            String normalizedName = normalizeProgramName(program.getName());
            String displayName = getProgramDisplayName(program, language);
            if (nameCounts.getOrDefault(normalizedName, 0) == 1) {
                labels.add(displayName);
                continue;
            }

            int occurrence = seenCounts.merge(normalizedName, 1, Integer::sum);
            labels.add(displayName + " (" + buildProgramDisambiguator(program, occurrence, language) + ")");
        }

        return labels;
    }

    private String buildProgramDisambiguator(Program program,
                                             int occurrence,
                                             com.example.fitnessbot.model.UserLanguage language) {
        if (program.getCreatedAt() != null) {
            return BotText.createdDisambiguator(program.getCreatedAt().format(PROGRAM_CREATED_AT_FORMATTER), language);
        }
        return BotText.optionDisambiguator(occurrence, language);
    }

    private String getProgramDisplayName(Program program, com.example.fitnessbot.model.UserLanguage language) {
        String name = program.getName();
        if (name == null || name.trim().isEmpty()) {
            return BotText.untitledProgram(language);
        }
        return name.trim();
    }

    private String normalizeProgramName(String programName) {
        if (programName == null) {
            return "";
        }
        return programName.trim().toLowerCase(Locale.ROOT);
    }

    private InlineKeyboardMarkup createProgramDetailsButtons(
            Long programId,
            List<com.example.fitnessbot.model.ProgramTrainingDay> trainingDays,
            com.example.fitnessbot.model.UserLanguage language) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton startButton = new InlineKeyboardButton();
        startButton.setText(BotText.startProgramButton(language));
        startButton.setCallbackData("start_program:" + programId);

        InlineKeyboardButton renameButton = new InlineKeyboardButton();
        renameButton.setText(BotText.renameButton(language));
        renameButton.setCallbackData("rename_program:" + programId);

        InlineKeyboardButton deleteButton = new InlineKeyboardButton();
        deleteButton.setText(BotText.deleteProgramButton(language));
        deleteButton.setCallbackData("delete_program:" + programId);
        rows.add(List.of(startButton, renameButton));
        rows.add(List.of(deleteButton));

        for (com.example.fitnessbot.model.ProgramTrainingDay programTrainingDay : trainingDays) {
            TrainingDay trainingDay = programTrainingDay.getTrainingDay();
            InlineKeyboardButton dayButton = new InlineKeyboardButton();
            dayButton.setText(BotText.dayButtonPrefix(language) + programTrainingDay.getPosition() + ": " + trainingDay.getTitle());
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
