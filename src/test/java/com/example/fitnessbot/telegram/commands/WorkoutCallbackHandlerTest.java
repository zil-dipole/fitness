package com.example.fitnessbot.telegram.commands;

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

    private WorkoutCallbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WorkoutCallbackHandler(workoutService);
    }

    @Test
    void canHandleWorkoutCallbacks() {
        assertThat(handler.canHandle(callback(WorkoutMessageFormatter.START_ACTIVE_DAY_CALLBACK))).isTrue();
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
        assertThat(response.getText()).contains("Training day started");
        assertThat(response.getText()).contains("Exercise 1/2");
        assertThat(response.getText()).contains("Bench Press");
        assertThat(response.getText()).contains("https://video.example/bench");
        assertThat(response.getText()).contains("25 Apr 2026: 55 / 57.5 / 60 kg");
        assertThat(response.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);
    }

    @Test
    void finishWorkoutShowsCompletionMessage() {
        when(workoutService.finishActiveWorkout(TELEGRAM_USER_ID)).thenReturn(true);

        SendMessage response = handler.handle(update(WorkoutMessageFormatter.FINISH_WORKOUT_CALLBACK));

        assertThat(response.getText()).isEqualTo("Training day finished.");
    }

    private WorkoutService.WorkoutExerciseView exerciseView() {
        return new WorkoutService.WorkoutExerciseView(
                100L,
                "Upper Body",
                "Bench Press",
                1,
                2,
                1,
                3,
                "8",
                "Warm up first",
                List.of("https://video.example/bench"),
                List.of(new WorkoutService.WorkoutHistoryEntry(
                        LocalDateTime.of(2026, 4, 25, 12, 0),
                        List.of(55.0, 57.5, 60.0)
                ))
        );
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
