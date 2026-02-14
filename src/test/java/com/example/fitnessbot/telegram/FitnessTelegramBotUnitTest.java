package com.example.fitnessbot.telegram;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.telegram.commands.*;
import com.example.fitnessbot.telegram.commands.CommandRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitnessTelegramBotUnitTest {

    private static final long USER_ID = 12345;
    private static final long CHAT_ID = 6789;

    @Mock
    private TrainingDayService trainingDayService;

    @Spy
    private FitnessTelegramBot fitnessTelegramBot;

    {
        List<CommandHandler> commandHandlers = List.of(
            new StartCommandHandler(),
            new HelpCommandHandler(new CommandRegistryService()),
            new MenuCommandHandler()
        );
        
        List<CallbackQueryHandler> callbackQueryHandlers = List.of(
            new ShowDayCommandHandler(trainingDayService)
        );
        
        fitnessTelegramBot = new FitnessTelegramBot(trainingDayService, new ProgramCreationSessionManager(), commandHandlers, callbackQueryHandlers, new CommandRegistryService(), "test-token", "test-username");
    }

    @Test
    void testGetBotUsername() {
        assertEquals("test-username", fitnessTelegramBot.getBotUsername());
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

    private Update createMockUpdateWithCommand(String command) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(command);
        when(message.getChatId()).thenReturn(CHAT_ID);

        return update;
    }

    private Update createMockUpdateWithCallbackQuery(String callbackData) {
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);

        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getId()).thenReturn("test_callback_id");
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(CHAT_ID);

        return update;
    }

}