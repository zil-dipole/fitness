package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.WorkoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutCallbackHandlerTest {

    private static final Long TELEGRAM_USER_ID = 12345L;
    private static final Long CHAT_ID = 6789L;

    @Mock
    private WorkoutService workoutService;

    @Mock
    private ProgramService programService;

    private WorkoutCallbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WorkoutCallbackHandler(workoutService, programService);
    }

    @Test
    void canHandleWorkoutCallbacks() {
        assertThat(handler.canHandle(callback(WorkoutMessageFormatter.START_ACTIVE_DAY_CALLBACK))).isTrue();
        assertThat(handler.canHandle(callback(WorkoutMessageFormatter.PREVIOUS_WEIGHT_CALLBACK))).isTrue();
        assertThat(handler.canHandle(callback(WorkoutMessageFormatter.NO_LOAD_CALLBACK))).isTrue();
        assertThat(handler.canHandle(callback(WorkoutMessageFormatter.SKIP_EXERCISE_CALLBACK))).isTrue();
        assertThat(handler.canHandle(callback(WorkoutMessageFormatter.FINISH_WORKOUT_CALLBACK))).isTrue();
        assertThat(handler.canHandle(callback("unknown"))).isFalse();
    }

    @Test
    void startActiveDayShowsFirstExercise() throws Exception {
        when(workoutService.startActiveTrainingDay(TELEGRAM_USER_ID)).thenReturn(exerciseView());

        SendMessage response = handler.handle(update(WorkoutMessageFormatter.START_ACTIVE_DAY_CALLBACK));

        assertThat(response.getChatId()).isEqualTo(String.valueOf(CHAT_ID));
        assertThat(response.getParseMode()).isEqualTo("HTML");
        assertThat(response.getText()).startsWith("🔥 <b>Bench Press</b>");
        assertThat(response.getText()).contains("Set 1/3 → <b>8 reps</b>");
        assertThat(response.getText()).contains("• Warm up first");
        assertThat(response.getText()).doesNotContain("Training day started");
        assertThat(response.getText()).doesNotContain("Now:");
        assertThat(response.getText()).doesNotContain("Exercise 1/2");
        assertThat(response.getText()).doesNotContain("Reps/Duration");
        assertThat(response.getText()).doesNotContain("Previous loads");
        assertThat(response.getText()).contains("Bench Press");
        assertThat(response.getText()).contains("🎥 https://video.example/bench");
        assertThat(response.getText()).contains("Last 25 Apr: 55 kg / red band / no load");
        assertThat(response.getText()).contains("Load for set 1");
        assertThat(response.getText()).contains("60 · red band · bodyweight · none");
        assertThat(response.getText().split("\\R")).hasSizeLessThanOrEqualTo(6);
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        assertThat(markup.getKeyboard().getFirst().getFirst().getText()).isEqualTo("Use 55 kg");
        assertThat(markup.getKeyboard().getFirst().getFirst().getCallbackData()).isEqualTo(WorkoutMessageFormatter.PREVIOUS_WEIGHT_CALLBACK);
        assertThat(markup.getKeyboard().get(1).getFirst().getText()).isEqualTo("No load");
        assertThat(markup.getKeyboard().get(1).getFirst().getCallbackData()).isEqualTo(WorkoutMessageFormatter.NO_LOAD_CALLBACK);
    }

    @Test
    void startActiveDayShowsPreviousCustomLoadButton() throws Exception {
        when(workoutService.startActiveTrainingDay(TELEGRAM_USER_ID))
                .thenReturn(exerciseView(null, "orange band"));

        SendMessage response = handler.handle(update(WorkoutMessageFormatter.START_ACTIVE_DAY_CALLBACK));

        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        assertThat(markup.getKeyboard().getFirst().getFirst().getText()).isEqualTo("Use orange band");
        assertThat(markup.getKeyboard().getFirst().getFirst().getCallbackData()).isEqualTo(WorkoutMessageFormatter.PREVIOUS_WEIGHT_CALLBACK);
    }

    @Test
    void noLoadButtonRecordsCurrentSetAsNoLoad() throws Exception {
        when(workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none"))
                .thenReturn(new WorkoutService.WeightEntryResult(true, false, "Saved set 1: no load.", exerciseView()));

        SendMessage response = handler.handle(update(WorkoutMessageFormatter.NO_LOAD_CALLBACK));

        assertThat(response.getParseMode()).isEqualTo("HTML");
        assertThat(response.getText()).contains("<b>no load saved</b> · set 1");
        assertThat(response.getText()).contains("🔥 <b>Bench Press</b>");
        assertThat(response.getText()).doesNotContain("Exercise 1/2");
        assertThat(response.getText().split("\\R")).hasSizeLessThanOrEqualTo(7);
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
        verify(workoutService).recordWeightForCurrentSet(TELEGRAM_USER_ID, "none");
    }

    @Test
    void previousWeightButtonRecordsCurrentSetWithPreviousWeight() throws Exception {
        when(workoutService.recordPreviousWeightForCurrentSet(TELEGRAM_USER_ID))
                .thenReturn(new WorkoutService.WeightEntryResult(true, false, "Saved set 1: 55 kg.", exerciseView()));

        SendMessage response = handler.handle(update(WorkoutMessageFormatter.PREVIOUS_WEIGHT_CALLBACK));

        assertThat(response.getParseMode()).isEqualTo("HTML");
        assertThat(response.getText()).contains("<b>55 kg saved</b> · set 1");
        assertThat(response.getText()).contains("🔥 <b>Bench Press</b>");
        assertThat(response.getText()).doesNotContain("Exercise 1/2");
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
        verify(workoutService).recordPreviousWeightForCurrentSet(TELEGRAM_USER_ID);
    }

    @Test
    void previousWeightButtonShowsMessageWhenNoPreviousWeightExists() throws Exception {
        when(workoutService.recordPreviousWeightForCurrentSet(TELEGRAM_USER_ID))
                .thenReturn(new WorkoutService.WeightEntryResult(
                        false,
                        false,
                        "No previous load is available for this exercise.",
                        null
                ));

        SendMessage response = handler.handle(update(WorkoutMessageFormatter.PREVIOUS_WEIGHT_CALLBACK));

        assertThat(response.getText()).isEqualTo("No previous load is available for this exercise.");
        assertThat(response.getParseMode()).isNull();
        assertThat(response.getReplyMarkup()).isNull();
    }

    @Test
    void finishWorkoutShowsCompletionMessage() {
        when(workoutService.finishActiveWorkout(TELEGRAM_USER_ID)).thenReturn(true);
        when(programService.advanceActiveTrainingDayForUser(TELEGRAM_USER_ID)).thenReturn(nextTrainingDayProgression(2, false, false));

        SendMessage response = handler.handle(update(WorkoutMessageFormatter.FINISH_WORKOUT_CALLBACK));

        assertThat(response.getParseMode()).isEqualTo("HTML");
        assertThat(response.getText()).contains("✅ <b>Training day complete</b>");
        assertThat(response.getText()).contains("Training day finished.");
        assertThat(response.getText()).contains("Next: <b>Lower Body</b>");
        assertThat(response.getText()).contains("Week 2 is ready.");
        assertThat(response.getText()).contains("Tap Start Day when you're ready.");
        assertThat(response.getText()).doesNotContain("Exercises:");
        assertThat(response.getText()).doesNotContain("Squat");
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
    }

    @Test
    void completedWorkoutResultShowsNextTrainingDay() throws Exception {
        when(workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none"))
                .thenReturn(new WorkoutService.WeightEntryResult(true, true, "Saved set 3: Grey green band. Training day completed.", null));
        when(programService.advanceActiveTrainingDayForUser(TELEGRAM_USER_ID)).thenReturn(nextTrainingDayProgression(2, false, false));

        SendMessage response = handler.handle(update(WorkoutMessageFormatter.NO_LOAD_CALLBACK));

        assertThat(response.getParseMode()).isEqualTo("HTML");
        assertThat(response.getText()).contains("✅ <b>Training day complete</b>");
        assertThat(response.getText()).contains("Saved set 3: Grey green band. Training day completed.");
        assertThat(response.getText()).contains("Next: <b>Lower Body</b>");
        assertThat(response.getText()).contains("Week 2 is ready.");
        assertThat(response.getText()).doesNotContain("Exercises:");
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
    }

    @Test
    void completedWorkoutResultShowsFiveWeekMessageAfterWrap() throws Exception {
        when(workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none"))
                .thenReturn(new WorkoutService.WeightEntryResult(true, true, "Saved set 3: Grey green band. Training day completed.", null));
        when(programService.advanceActiveTrainingDayForUser(TELEGRAM_USER_ID)).thenReturn(nextTrainingDayProgression(6, true, true));

        SendMessage response = handler.handle(update(WorkoutMessageFormatter.NO_LOAD_CALLBACK));

        assertThat(response.getParseMode()).isEqualTo("HTML");
        assertThat(response.getText()).contains("🏁 <b>5 weeks completed</b>");
        assertThat(response.getText()).contains("You completed 5 weeks of this program.");
        assertThat(response.getText()).contains("Next: <b>Lower Body</b>");
        assertThat(response.getText()).contains("Week 6 is ready.");
    }

    @Test
    void completedWorkoutResultShowsFinishScreenWithoutStartButtonWhenNoNextDay() throws Exception {
        when(workoutService.recordWeightForCurrentSet(TELEGRAM_USER_ID, "none"))
                .thenReturn(new WorkoutService.WeightEntryResult(true, true, "Saved set 3: no load. Training day completed.", null));
        when(programService.advanceActiveTrainingDayForUser(TELEGRAM_USER_ID)).thenReturn(null);

        SendMessage response = handler.handle(update(WorkoutMessageFormatter.NO_LOAD_CALLBACK));

        assertThat(response.getParseMode()).isEqualTo("HTML");
        assertThat(response.getText()).contains("✅ <b>Training day complete</b>");
        assertThat(response.getText()).contains("Saved set 3: no load. Training day completed.");
        assertThat(response.getText()).doesNotContain("Next:");
        assertThat(response.getReplyMarkup()).isNull();
    }

    private WorkoutService.WorkoutExerciseView exerciseView() {
        return exerciseView(55.0, "55 kg");
    }

    private WorkoutService.WorkoutExerciseView exerciseView(Double previousWeightKg, String previousLoad) {
        return new WorkoutService.WorkoutExerciseView(
                100L,
                "Upper Body",
                "Bench Press",
                1,
                2,
                1,
                3,
                false,
                "8",
                "Warm up first",
                List.of("https://video.example/bench"),
                previousWeightKg,
                previousLoad,
                List.of(new WorkoutService.WorkoutHistoryEntry(
                        LocalDateTime.of(2026, 4, 25, 12, 0),
                        List.of("55 kg", "red band", "no load")
                ))
        );
    }

    private ProgramService.ActiveTrainingDayProgression nextTrainingDayProgression(int weekNumber, boolean wrappedToFirstDay, boolean completedFiveWeeks) {
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setTitle("Lower Body");
        Exercise exercise = new Exercise();
        exercise.setName("Squat");
        trainingDay.setExercises(List.of(exercise));
        return new ProgramService.ActiveTrainingDayProgression(trainingDay, weekNumber, wrappedToFirstDay, completedFiveWeeks);
    }

    private Update update(String data) {
        Update update = new Update();
        update.setCallbackQuery(callback(data));
        return update;
    }

    private CallbackQuery callback(String data) {
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        lenient().when(callbackQuery.getData()).thenReturn(data);
        lenient().when(callbackQuery.getMessage()).thenReturn(message);
        lenient().when(message.getChatId()).thenReturn(CHAT_ID);
        lenient().when(callbackQuery.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(TELEGRAM_USER_ID);
        return callbackQuery;
    }
}
