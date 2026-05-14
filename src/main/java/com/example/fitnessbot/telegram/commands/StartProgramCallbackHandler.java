package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.exception.WorkoutException;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.service.WorkoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class StartProgramCallbackHandler implements CallbackQueryHandler {

    private static final String CALLBACK_PREFIX = "start_program:";

    private final ProgramService programService;
    private final WorkoutService workoutService;
    private final UserLanguageService languageService;

    @Autowired
    public StartProgramCallbackHandler(ProgramService programService,
                                       WorkoutService workoutService,
                                       UserLanguageService languageService) {
        this.programService = programService;
        this.workoutService = workoutService;
        this.languageService = languageService;
    }

    public StartProgramCallbackHandler(ProgramService programService, WorkoutService workoutService) {
        this.programService = programService;
        this.workoutService = workoutService;
        this.languageService = null;
    }

    @Override
    public boolean canHandle(CallbackQuery callbackQuery) {
        return callbackQuery.getData() != null && callbackQuery.getData().startsWith(CALLBACK_PREFIX);
    }

    @Override
    public SendMessage handle(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        SendMessage response = new SendMessage();
        response.setChatId(callbackQuery.getMessage().getChatId().toString());
        var language = BotText.language(languageService, callbackQuery.getFrom().getId());

        Long programId;
        try {
            programId = Long.parseLong(callbackQuery.getData().substring(CALLBACK_PREFIX.length()));
        } catch (NumberFormatException e) {
            response.setText(BotText.invalidProgramId(language));
            return response;
        }

        try {
            ProgramService.ActiveProgramSelection selection =
                    programService.startProgramForUser(programId, callbackQuery.getFrom().getId());
            WorkoutService.WorkoutExerciseView view = workoutService.startActiveTrainingDay(callbackQuery.getFrom().getId());
            response.setText(BotText.programStarted(selection.program().getName(), language)
                    + WorkoutMessageFormatter.formatExerciseView(view, language));
            response.setParseMode("HTML");
            response.setReplyMarkup(WorkoutMessageFormatter.exerciseKeyboard(view, language));
        } catch (ProgramException | WorkoutException e) {
            response.setText(localizeStartError(e.getMessage(), language));
        }

        return response;
    }

    private String localizeStartError(String message, com.example.fitnessbot.model.UserLanguage language) {
        if ("Program not found.".equals(message)) {
            return BotText.programNotFound(language);
        }
        if ("Cannot start a program without training days.".equals(message)) {
            return BotText.programCannotStartEmpty(language);
        }
        if ("You don't have an active training day.".equals(message)) {
            return BotText.workoutNoActiveTrainingDay(language);
        }
        if ("Active training day has no exercises.".equals(message)) {
            return BotText.workoutTrainingDayNoExercises(language);
        }
        return message;
    }
}
