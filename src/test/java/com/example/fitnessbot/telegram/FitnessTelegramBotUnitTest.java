package com.example.fitnessbot.telegram;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.telegram.commands.CommandHandler;
import com.example.fitnessbot.telegram.commands.HelpCommandHandler;
import com.example.fitnessbot.telegram.commands.StartCommandHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

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
        List<CommandHandler> commandHandlers = List.of(new StartCommandHandler(), new HelpCommandHandler());
        fitnessTelegramBot = new FitnessTelegramBot(trainingDayService, new ProgramCreationSessionManager(), commandHandlers, "test-token", "test-username");
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

        SendMessage message = new SendMessage();
        message.setChatId(CHAT_ID);
        message.setText("Welcome to Fitness Bot! Forward your workout programs to me and I'll parse and save them for you.");
        verify(fitnessTelegramBot).sendTelegramMessage(message);
    }

    @Test
    void testHandleHelpCommand() throws Exception {
        Update update = createMockUpdateWithCommand("/help");
        
        // Mock the sendTelegramMessage method to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));
        
        fitnessTelegramBot.onUpdateReceived(update);

        SendMessage message = new SendMessage();
        message.setChatId(CHAT_ID);
        message.setText("""
                Simply forward your workout program messages to me and I'll parse and save them.
                
                Supported format:
                - Section headers ending with ':'
                - Exercises with bullet points ('⁃' or '-')
                - Sets and reps like "3 x 10"
                - Video links
                
                Program Creation Commands:
                - /create_program <name> - Start creating a new program
                - Forward training day messages to add them to the program
                - /finish_program - Finish and save the program
                - /cancel_program - Cancel program creation
                
                Example:
                Upper Body:
                - Bench Press 3 x 10 (Warm up set)
                - https://youtube.com/watch?v=example""");

        verify(fitnessTelegramBot).sendTelegramMessage(message);
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

}