package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.UserLanguage;
import com.example.fitnessbot.service.UserLanguageService;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public final class BotText {

    private static final MessageSource FALLBACK_MESSAGE_SOURCE = fallbackMessageSource();
    private static volatile MessageSource messageSource = FALLBACK_MESSAGE_SOURCE;

    public BotText(MessageSource messageSource) {
        BotText.messageSource = messageSource == null ? FALLBACK_MESSAGE_SOURCE : messageSource;
    }

    public static UserLanguage language(UserLanguageService languageService, Long telegramUserId) {
        if (languageService == null) {
            return UserLanguage.ENGLISH;
        }
        return languageService.getLanguage(telegramUserId);
    }

    public static String mainMenuTitle(UserLanguage language) {
        return msg("menu.title", language);
    }

    public static String createProgramButton(UserLanguage language) {
        return msg("menu.button.createProgram", language);
    }

    public static String viewProgramsButton(UserLanguage language) {
        return msg("menu.button.viewPrograms", language);
    }

    public static String finishProgramButton(UserLanguage language) {
        return msg("menu.button.finishProgram", language);
    }

    public static String cancelProgramButton(UserLanguage language) {
        return msg("menu.button.cancelProgram", language);
    }

    public static String languageButton(UserLanguage language) {
        return msg("menu.button.language", language);
    }

    public static String helpButton(UserLanguage language) {
        return msg("menu.button.help", language);
    }

    public static String startAlreadySetup(UserLanguage language) {
        return msg("start.alreadySetup", language);
    }

    public static String startWelcome(UserLanguage language) {
        return msg("start.welcome", language);
    }

    public static String helpText(UserLanguage language, Iterable<CommandMetadata> commands) {
        StringBuilder helpText = new StringBuilder();
        helpText.append("<b>").append(msg("help.how.title", language)).append("</b>\n");
        helpText.append(msg("help.how.step1", language)).append("\n");
        helpText.append(msg("help.how.step2", language)).append("\n");
        helpText.append(msg("help.how.step3", language)).append("\n\n");

        helpText.append("<b>").append(msg("help.format.title", language)).append("</b>\n");
        helpText.append(msg("help.format.sections", language)).append("\n");
        helpText.append(msg("help.format.exercises", language)).append("\n");
        helpText.append(msg("help.format.sets", language)).append("\n");
        helpText.append(msg("help.format.videos", language)).append("\n\n");

        helpText.append("<b>").append(msg("help.commands.title", language)).append("</b>\n");
        appendCommands(helpText, language, commands);

        helpText.append("\n<b>").append(msg("help.example.title", language)).append("</b>\n");
        helpText.append(msg("help.example", language)).append("\n");
        helpText.append(msg("help.footer", language));
        return helpText.toString();
    }

    public static String commandDescription(String command, UserLanguage language) {
        String normalizedCommand = command == null ? "" : command.replace("/", "");
        return msg("command." + normalizedCommand + ".description", language);
    }

    public static String createProgramUnavailable(UserLanguage language) {
        return msg("createProgram.unavailable", language);
    }

    public static String programDraftCreated(String programName, UserLanguage language) {
        return msg("createProgram.success", language, programName, finishProgramButton(language));
    }

    public static String createProgramGenericError(UserLanguage language) {
        return msg("createProgram.error.generic", language);
    }

    public static String finishProgramNoSession(UserLanguage language) {
        return msg("finishProgram.noSession", language);
    }

    public static String finishProgramEmpty(UserLanguage language) {
        return msg("finishProgram.empty", language);
    }

    public static String finishProgramSuccess(String programName, int trainingDaysCount, UserLanguage language) {
        return msg("finishProgram.success", language, programName, trainingDaysCount(trainingDaysCount, language));
    }

    public static String finishProgramGenericError(UserLanguage language) {
        return msg("finishProgram.error.generic", language);
    }

    public static String cancelProgramNoSession(UserLanguage language) {
        return msg("cancelProgram.noSession", language);
    }

    public static String cancelProgramSuccess(UserLanguage language) {
        return msg("cancelProgram.success", language);
    }

    public static String showProgramUnavailable(UserLanguage language) {
        return msg("showProgram.unavailable", language);
    }

    public static String invalidProgramId(UserLanguage language) {
        return msg("program.invalidId", language);
    }

    public static String savedProgramsHeader(UserLanguage language) {
        return msg("showProgram.saved.header", language);
    }

    public static String noSavedPrograms(UserLanguage language) {
        return msg("showProgram.saved.empty", language);
    }

    public static String openProgramHint(String exampleName, UserLanguage language) {
        return msg("showProgram.saved.openHint", language, TrainingDayMessageFormatter.escapeHtml(exampleName));
    }

    public static String programNotFound(UserLanguage language) {
        return msg("program.notFound", language);
    }

    public static String programCannotStartEmpty(UserLanguage language) {
        return msg("program.cannotStart.empty", language);
    }

    public static String programNotFoundShowList(UserLanguage language) {
        return msg("program.notFound.showList", language);
    }

    public static String multipleProgramsFound(String programName, UserLanguage language) {
        return msg("program.multipleFound", language, programName);
    }

    public static String programDetailsHeader(String programName, UserLanguage language) {
        return msg("showProgram.details.header", language, TrainingDayMessageFormatter.escapeHtml(programName));
    }

    public static String trainingDaysHeader(UserLanguage language) {
        return msg("showProgram.details.trainingDays", language);
    }

    public static String noLinkedTrainingDays(UserLanguage language) {
        return msg("showProgram.details.noTrainingDays", language);
    }

    public static String trainingDaysTotal(int count, UserLanguage language) {
        return msg("showProgram.details.total", language, count, trainingDayNoun(count, language));
    }

    public static String tapStartProgram(UserLanguage language) {
        return msg("showProgram.details.startHint", language);
    }

    public static String startProgramButton(UserLanguage language) {
        return msg("program.button.start", language);
    }

    public static String renameButton(UserLanguage language) {
        return msg("program.button.rename", language);
    }

    public static String deleteProgramButton(UserLanguage language) {
        return msg("program.button.delete", language);
    }

    public static String dayButtonPrefix(UserLanguage language) {
        return msg("program.button.dayPrefix", language);
    }

    public static String createdDisambiguator(String createdAt, UserLanguage language) {
        return msg("program.disambiguator.created", language, createdAt);
    }

    public static String optionDisambiguator(int occurrence, UserLanguage language) {
        return msg("program.disambiguator.option", language, occurrence);
    }

    public static String untitledProgram(UserLanguage language) {
        return msg("program.untitled", language);
    }

    public static String programDraftTitle(String programName, UserLanguage language) {
        return msg("showProgram.draft.header", language, TrainingDayMessageFormatter.escapeHtml(programName));
    }

    public static String noTrainingDaysAdded(UserLanguage language) {
        return msg("showProgram.draft.empty", language);
    }

    public static String trainingDaysAddedHeader(UserLanguage language) {
        return msg("showProgram.draft.trainingDays", language);
    }

    public static String draftProgress(int count, UserLanguage language) {
        return msg("showProgram.draft.progress", language, trainingDaysCount(count, language), finishProgramButton(language));
    }

    public static String activeProgramMissing(UserLanguage language) {
        return msg("activeDay.noProgram", language);
    }

    public static String activeDayHeader(int weekNumber, UserLanguage language) {
        return msg("activeDay.header", language, weekNumber);
    }

    public static String showDayInvalidId(UserLanguage language) {
        return msg("showDay.invalidId", language);
    }

    public static String showDayNotFound(UserLanguage language) {
        return msg("showDay.notFound", language);
    }

    public static String showDayUnauthorized(UserLanguage language) {
        return msg("showDay.unauthorized", language);
    }

    public static String languagePrompt(UserLanguage currentLanguage) {
        return msg("language.prompt", currentLanguage, msg("language.name", currentLanguage));
    }

    public static String languageChanged(UserLanguage language) {
        return msg("language.changed", language);
    }

    public static String invalidLanguage(UserLanguage language) {
        return msg("language.invalid", language);
    }

    public static String englishButton() {
        return msg("language.english", UserLanguage.ENGLISH);
    }

    public static String russianButton() {
        return msg("language.russian", UserLanguage.RUSSIAN);
    }

    public static String trainingDaySaved(int exerciseCount, UserLanguage language) {
        return msg("trainingDay.saved", language, exerciseCount, exerciseNoun(exerciseCount, language));
    }

    public static String addedTrainingDayToDraft(String trainingDayTitle,
                                                 String programName,
                                                 int trainingDaysCount,
                                                 UserLanguage language) {
        return msg(
                "trainingDay.draft.added",
                language,
                trainingDayTitle,
                programName,
                trainingDaysCount(trainingDaysCount, language),
                finishProgramButton(language)
        );
    }

    public static String fallbackTrainingDayTitle(UserLanguage language) {
        return msg("trainingDay.fallbackTitle", language);
    }

    public static String invalidTrainingDayMessage(UserLanguage language) {
        return msg("trainingDay.error.invalidMessage", language);
    }

    public static String invalidTrainingDayDuringProgram(UserLanguage language) {
        return msg("trainingDay.error.invalidDraftDay", language);
    }

    public static String parseTrainingDayError(UserLanguage language) {
        return msg("trainingDay.error.parse", language);
    }

    public static String databaseTrainingDayError(UserLanguage language) {
        return msg("trainingDay.error.database", language);
    }

    public static String unexpectedTrainingDaySaveError(UserLanguage language) {
        return msg("trainingDay.error.unexpected", language);
    }

    public static String addTrainingDayToDraftError(UserLanguage language) {
        return msg("trainingDay.error.addToDraft", language);
    }

    public static String callbackUnknown(UserLanguage language) {
        return msg("callback.unknown", language);
    }

    public static String callbackError(UserLanguage language) {
        return msg("callback.error", language);
    }

    public static String executingCommand(String command, UserLanguage language) {
        return msg("command.executing", language, command);
    }

    public static String unknownCommand(UserLanguage language) {
        return msg("command.unknown", language);
    }

    public static String commandProcessingError(UserLanguage language) {
        return msg("command.error", language);
    }

    public static String chooseCommand(UserLanguage language) {
        return msg("command.choose", language);
    }

    public static String commandSuggestions(UserLanguage language) {
        return msg("command.suggestions", language);
    }

    public static String renameProgramPrompt(String programName, UserLanguage language) {
        return msg("program.rename.prompt", language, programName);
    }

    public static String programRenamed(String programName, Long programId, UserLanguage language) {
        return msg("program.rename.success", language, programName, programId);
    }

    public static String emptyProgramName(UserLanguage language) {
        return msg("program.rename.empty", language);
    }

    public static String renameProgramError(UserLanguage language) {
        return msg("program.rename.error", language);
    }

    public static String workoutNoActiveSession(UserLanguage language) {
        return msg("workout.noActiveSession", language);
    }

    public static String workoutNoActiveTrainingDay(UserLanguage language) {
        return msg("workout.noActiveTrainingDay", language);
    }

    public static String workoutTrainingDayNoExercises(UserLanguage language) {
        return msg("workout.noExercises", language);
    }

    public static String workoutCurrentExerciseMissing(UserLanguage language) {
        return msg("workout.currentExerciseMissing", language);
    }

    public static String workoutSaveError(UserLanguage language) {
        return msg("workout.saveError", language);
    }

    public static String workoutNoPreviousLoad(UserLanguage language) {
        return msg("workout.noPreviousLoad", language);
    }

    public static String programDeleted(UserLanguage language) {
        return msg("program.deleted", language);
    }

    public static String programStarted(String programName, UserLanguage language) {
        return msg("program.started", language, TrainingDayMessageFormatter.escapeHtml(programName));
    }

    public static String workoutFinishTitle(boolean completedFiveWeeks, UserLanguage language) {
        return msg(completedFiveWeeks ? "workout.finish.fiveTitle" : "workout.finish.title", language);
    }

    public static String workoutFinishedManually(UserLanguage language) {
        return msg("workout.finish.manual", language);
    }

    public static String workoutFiveWeeksDone(UserLanguage language) {
        return msg("workout.finish.fiveMessage", language);
    }

    public static String workoutNextDay(String title, UserLanguage language) {
        return msg("workout.finish.next", language, title);
    }

    public static String workoutWeekReady(int weekNumber, UserLanguage language) {
        return msg("workout.finish.weekReady", language, weekNumber);
    }

    public static String workoutStartDayHint(UserLanguage language) {
        return msg("workout.finish.startHint", language);
    }

    public static String workoutStepLabel(boolean circuit, boolean capitalized, UserLanguage language) {
        if (circuit) {
            return msg(capitalized ? "workout.label.round.cap" : "workout.label.round", language);
        }
        return msg(capitalized ? "workout.label.set.cap" : "workout.label.set", language);
    }

    public static String workoutLoadPrompt(String promptLabel, int setNumber, UserLanguage language) {
        return msg("workout.prompt.loadFor", language, promptLabel, setNumber, msg("workout.prompt.examples", language));
    }

    public static String workoutStartDayButton(UserLanguage language) {
        return msg("workout.button.startDay", language);
    }

    public static String workoutUsePreviousButton(String previousLoad, UserLanguage language) {
        return msg("workout.button.usePrevious", language, previousLoad);
    }

    public static String workoutNoLoadButton(UserLanguage language) {
        return msg("workout.button.noLoad", language);
    }

    public static String workoutSkipButton(UserLanguage language) {
        return msg("workout.button.skip", language);
    }

    public static String workoutFinishDayButton(UserLanguage language) {
        return msg("workout.button.finish", language);
    }

    public static String workoutHistoryPrefix(UserLanguage language) {
        return msg("workout.history.prefix", language);
    }

    public static String workoutSavedDefault(UserLanguage language) {
        return msg("workout.saved.default", language);
    }

    public static String workoutSavedLoad(String load, String stepLabel, String stepNumber, UserLanguage language) {
        return msg("workout.saved.load", language, load, stepLabel, stepNumber);
    }

    public static String workoutSkipped(UserLanguage language) {
        return msg("workout.skipped", language);
    }

    public static String workoutPrescribedWork(UserLanguage language) {
        return msg("workout.prescribed", language);
    }

    public static String workoutReps(String reps, UserLanguage language) {
        return msg("workout.reps", language, reps);
    }

    public static String noLoadDisplay(UserLanguage language) {
        return msg("load.noLoad", language);
    }

    public static String trainingDayExercisesLabel(UserLanguage language) {
        return msg("trainingDay.format.exercises", language);
    }

    public static String trainingDayNotesLabel(UserLanguage language) {
        return msg("trainingDay.format.notes", language);
    }

    public static String trainingDayVideosLabel(UserLanguage language) {
        return msg("trainingDay.format.videos", language);
    }

    public static String trainingDayNoExercises(UserLanguage language) {
        return msg("trainingDay.format.noExercises", language);
    }

    public static String trainingDaysCount(int count, UserLanguage language) {
        if (isRussian(language)) {
            return count + " " + pluralRu(count, "тренировочный день", "тренировочных дня", "тренировочных дней");
        }
        return count + " training " + (count == 1 ? "day" : "days");
    }

    public static boolean isRussian(UserLanguage language) {
        return language == UserLanguage.RUSSIAN;
    }

    private static void appendCommands(StringBuilder helpText, UserLanguage language, Iterable<CommandMetadata> commands) {
        for (CommandMetadata cmd : commands) {
            helpText.append("• <b>")
                    .append(TrainingDayMessageFormatter.escapeHtml(cmd.getCommand()))
                    .append("</b> - ")
                    .append(TrainingDayMessageFormatter.escapeHtml(commandDescription(cmd.getCommand(), language)))
                    .append("\n");
            if (cmd.getUsageExample() != null && !cmd.getUsageExample().isEmpty()
                    && !cmd.getUsageExample().equals(cmd.getCommand())) {
                helpText.append(msg("help.command.examplePrefix", language))
                        .append("<code>")
                        .append(TrainingDayMessageFormatter.escapeHtml(cmd.getUsageExample()))
                        .append("</code>\n");
            }
        }
    }

    private static String msg(String key, UserLanguage language, Object... args) {
        Object[] argsToUse = args == null || args.length == 0 ? null : args;
        return messageSource.getMessage(key, argsToUse, locale(language));
    }

    private static Locale locale(UserLanguage language) {
        return isRussian(language) ? Locale.forLanguageTag("ru") : Locale.ENGLISH;
    }

    private static String trainingDayNoun(int count, UserLanguage language) {
        if (isRussian(language)) {
            return pluralRu(count, "день", "дня", "дней");
        }
        return count == 1 ? "day" : "days";
    }

    private static String exerciseNoun(int count, UserLanguage language) {
        if (isRussian(language)) {
            return pluralRu(count, "упражнение", "упражнения", "упражнений");
        }
        return count == 1 ? "exercise" : "exercises";
    }

    private static String pluralRu(int count, String one, String few, String many) {
        int mod100 = Math.abs(count) % 100;
        int mod10 = Math.abs(count) % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        if (mod10 == 1) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return few;
        }
        return many;
    }

    private static MessageSource fallbackMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
