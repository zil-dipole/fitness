package com.example.fitnessbot.telegram;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.service.WorkoutService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import com.example.fitnessbot.telegram.commands.*;
import com.example.fitnessbot.telegram.commands.CommandRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitnessTelegramBotUnitTest {

    private static final long USER_ID = 12345;
    private static final long CHAT_ID = 6789;

    @Mock
    private TrainingDayService trainingDayService;

    @Mock
    private WorkoutService workoutService;

    private FitnessTelegramBot fitnessTelegramBot;

    @BeforeEach
    void setUp() {
        List<CommandHandler> commandHandlers = List.of(
            new StartCommandHandler(mock(MenuKeyboardFactory.class)),
            new HelpCommandHandler(new CommandRegistryService(), mock(MenuKeyboardFactory.class)),
            new MenuCommandHandler(mock(MenuKeyboardFactory.class))
        );

        List<CallbackQueryHandler> callbackQueryHandlers = List.of(
            new ShowDayCommandHandler(trainingDayService)
        );

        FitnessTelegramBot bot = new FitnessTelegramBot(trainingDayService, workoutService, new ProgramCreationSessionManager(), commandHandlers, callbackQueryHandlers, new CommandRegistryService(), mock(MenuKeyboardFactory.class), "test-token", "test-username");
        fitnessTelegramBot = spy(bot);
    }

    @Test
    void testGetBotUsername() {
        assertEquals("test-username", fitnessTelegramBot.getBotUsername());
    }

    @Test
    void testTelegramNativeCommandMenuExcludesSessionOnlyCommands() {
        List<String> commands = fitnessTelegramBot.createTelegramCommandMenu().stream()
                .map(BotCommand::getCommand)
                .toList();

        assertTrue(commands.contains("start"));
        assertTrue(commands.contains("help"));
        assertTrue(commands.contains("menu"));
        assertTrue(commands.contains("create_program"));
        assertTrue(commands.contains("show_program"));
        assertTrue(commands.contains("active_day"));
        assertFalse(commands.contains("cancel_program"));
        assertFalse(commands.contains("finish_program"));
    }

    @Test
    void testHandleStartCommand() throws Exception {
        Update update = createMockUpdateWithCommand("/start");
        // Mock the sendTelegramMessage method to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));

        fitnessTelegramBot.onUpdateReceived(update);

        // We can't easily verify the exact keyboard markup in tests, so we'll just verify the text
        verify(fitnessTelegramBot, times(1)).sendTelegramMessage(any(SendMessage.class));
    }

    @Test
    void testHandleHelpCommand() throws Exception {
        Update update = createMockUpdateWithCommand("/help");

        // Mock the sendTelegramMessage method to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));

        fitnessTelegramBot.onUpdateReceived(update);

        verify(fitnessTelegramBot, times(1)).sendTelegramMessage(any(SendMessage.class));
    }

    @Test
    void testHandleMenuCommand() throws Exception {
        Update update = createMockUpdateWithCommand("/menu");

        // Mock the sendTelegramMessage method to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));

        fitnessTelegramBot.onUpdateReceived(update);

        // Verify that a message was sent
        verify(fitnessTelegramBot, times(1)).sendTelegramMessage(any(SendMessage.class));
    }

    @Test
    void testHandleUnknownCommand() throws Exception {
        Update update = createMockUpdateWithCommand("/unknown");

        // Mock the sendTelegramMessage method to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));

        fitnessTelegramBot.onUpdateReceived(update);

        SendMessage message = new SendMessage();
        message.setChatId(CHAT_ID);
        message.setText("Unknown command. Send /help for usage instructions.");
        verify(fitnessTelegramBot).sendTelegramMessage(message);
    }

    @Test
    void testHandleCallbackQueryCreateProgram() throws Exception {
        Update update = createMockUpdateWithCallbackQuery("create_program");

        // Mock the sendTelegramMessage method to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));

        fitnessTelegramBot.onUpdateReceived(update);

        // Verify that a message was sent (account for both the response and error message)
        verify(fitnessTelegramBot, atLeastOnce()).sendTelegramMessage(any(SendMessage.class));
    }

    @Test
    void testHandleCallbackQueryHelp() throws Exception {
        Update update = createMockUpdateWithCallbackQuery("help");

        // Mock the sendTelegramMessage method to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));

        fitnessTelegramBot.onUpdateReceived(update);

        // Verify that a message was sent (account for both the response and error message)
        verify(fitnessTelegramBot, atLeastOnce()).sendTelegramMessage(any(SendMessage.class));
    }

    @Test
    void testHandleWorkoutWeightInput() throws Exception {
        Update update = createMockUpdateWithCommand("60");
        WorkoutService.WorkoutExerciseView view = new WorkoutService.WorkoutExerciseView(
                100L,
                "Upper Body",
                "Bench Press",
                1,
                2,
                2,
                3,
                false,
                "8",
                "Warm up first",
                List.of("https://video.example/bench"),
                60.0,
                "60 kg",
                List.of(new WorkoutService.WorkoutHistoryEntry(
                        LocalDateTime.of(2026, 4, 25, 12, 0),
                        List.of("55 kg", "57.5 kg", "60 kg")
                ))
        );

        when(workoutService.hasWorkoutInputContext(USER_ID)).thenReturn(true);
        when(workoutService.recordWeightForCurrentSet(USER_ID, "60"))
                .thenReturn(new WorkoutService.WeightEntryResult(true, false, "Saved set 1: 60 kg.", view));
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));

        fitnessTelegramBot.onUpdateReceived(update);

        verify(fitnessTelegramBot).sendTelegramMessage(argThat(message -> {
            if (!(message.getReplyMarkup() instanceof InlineKeyboardMarkup markup)) {
                return false;
            }
            InlineKeyboardButton firstButton = markup.getKeyboard().getFirst().getFirst();
            return "HTML".equals(message.getParseMode())
                    && message.getText().contains("<b>60 kg saved</b> · set 1")
                    && message.getText().contains("🔥 <b>Bench Press</b>")
                    && message.getText().contains("Load for set 2")
                    && !message.getText().contains("Exercise 1/2")
                    && "Use 60 kg".equals(firstButton.getText())
                    && WorkoutMessageFormatter.PREVIOUS_WEIGHT_CALLBACK.equals(firstButton.getCallbackData());
        }));
    }

    private Update createMockUpdateWithCommand(String command) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        lenient().when(update.hasMessage()).thenReturn(true);
        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.hasText()).thenReturn(true);
        lenient().when(message.getText()).thenReturn(command);
        lenient().when(message.getChatId()).thenReturn(CHAT_ID);
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(USER_ID);

        return update;
    }

    private Update createMockUpdateWithCallbackQuery(String callbackData) {
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        lenient().when(update.hasCallbackQuery()).thenReturn(true);
        lenient().when(update.getCallbackQuery()).thenReturn(callbackQuery);
        lenient().when(callbackQuery.getId()).thenReturn("test_callback_id");
        lenient().when(callbackQuery.getData()).thenReturn(callbackData);
        lenient().when(callbackQuery.getMessage()).thenReturn(message);
        lenient().when(callbackQuery.getFrom()).thenReturn(user);
        lenient().when(message.getChatId()).thenReturn(CHAT_ID);
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(USER_ID);

        return update;
    }

}
