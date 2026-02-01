package com.example.fitnessbot.telegram;

import com.example.fitnessbot.service.TrainingDayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitnessTelegramBotUnitTest {

    @Mock
    private TrainingDayService trainingDayService;

    @Spy
    private FitnessTelegramBot fitnessTelegramBot = new FitnessTelegramBot(trainingDayService, "test-token", "test-username");

    @Test
    void testGetBotToken() {
        assertEquals("test-token", fitnessTelegramBot.getBotToken());
    }

    @Test
    void testGetBotUsername() {
        assertEquals("test-username", fitnessTelegramBot.getBotUsername());
    }

    @Test
    void testHandleStartCommand() throws Exception {
        Update update = createMockUpdateWithCommand("/start", 12345, 67890L);
        
        // Mock the sendTelegramMessage method to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));
        
        fitnessTelegramBot.onUpdateReceived(update);
        
        verify(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));
    }

    @Test
    void testHandleHelpCommand() throws Exception {
        Update update = createMockUpdateWithCommand("/help", 12345, 67890L);
        
        // Mock the sendTelegramMessage method to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));
        
        fitnessTelegramBot.onUpdateReceived(update);
        
        verify(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));
    }

    @Test
    void testHandleUnknownCommand() throws Exception {
        Update update = createMockUpdateWithCommand("/unknown", 12345, 67890L);
        
        // Mock the sendTelegramMessage method to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));
        
        fitnessTelegramBot.onUpdateReceived(update);
        
        verify(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));
    }

    // Helper methods to create mock updates
    
    private Update createMockUpdateWithCommand(String command, int userId, Long chatId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);
        Chat chat = mock(Chat.class);
        
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(command);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(userId);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getChat()).thenReturn(chat);
        when(chat.getId()).thenReturn(chatId);
        
        return update;
    }
    
    private Update createMockUpdateWithForwardedMessage(String text, int userId, Long chatId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);
        Chat chat = mock(Chat.class);
        User forwardFrom = mock(User.class);
        
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(userId);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getChat()).thenReturn(chat);
        when(chat.getId()).thenReturn(chatId);
        when(message.getForwardFrom()).thenReturn(forwardFrom);
        
        return update;
    }
    
    private Update createMockUpdateWithText(String text, int userId, Long chatId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);
        Chat chat = mock(Chat.class);
        
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(userId);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getChat()).thenReturn(chat);
        when(chat.getId()).thenReturn(chatId);
        when(message.getForwardFrom()).thenReturn(null);
        when(message.getForwardFromChat()).thenReturn(null);
        
        return update;
    }
}