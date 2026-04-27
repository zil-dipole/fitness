package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelProgramCommandHandlerTest {
    
    private static final Long TEST_TELEGRAM_ID = 12345L;
    private static final Long TEST_CHAT_ID = 6789L;

    @Mock
    private ProgramCreationSessionManager sessionManager;

    private CancelProgramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CancelProgramCommandHandler(sessionManager, mock(MenuKeyboardFactory.class));
    }

    @Test
    void testCanHandle() {
        assertThat(handler.canHandle("/cancel_program")).isTrue();
        assertThat(handler.canHandle("/create_program")).isFalse();
        assertThat(handler.canHandle("/finish_program")).isFalse();
        assertThat(handler.canHandle("/start")).isFalse();
    }

    @Test
    void testIsAvailableWithActiveSession() {
        when(sessionManager.hasActiveSession(TEST_TELEGRAM_ID)).thenReturn(true);
        assertThat(handler.isAvailable(TEST_TELEGRAM_ID, sessionManager)).isTrue();
    }

    @Test
    void testIsAvailableWithoutActiveSession() {
        when(sessionManager.hasActiveSession(TEST_TELEGRAM_ID)).thenReturn(false);
        assertThat(handler.isAvailable(TEST_TELEGRAM_ID, sessionManager)).isFalse();
    }

    @Test
    void testHandleUnavailable() {
        Update update = createMockUpdateWithCommand();
        SendMessage response = handler.handleUnavailable(update);

        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).isEqualTo("There isn't a program draft to cancel.");
        
        // Verify that no unexpected interactions occurred
        verifyNoMoreInteractions(sessionManager);
    }

    @Test
    void testHandleWithoutActiveSession() {
        // Given
        Update update = createMockUpdateWithCommand();
        when(sessionManager.hasActiveSession(TEST_TELEGRAM_ID)).thenReturn(false);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("There isn't a program draft to cancel");
        
        verify(sessionManager).hasActiveSession(TEST_TELEGRAM_ID);
        verifyNoMoreInteractions(sessionManager);
    }

    @Test
    void testHandleSuccess() {
        // Given
        Update update = createMockUpdateWithCommand();
        when(sessionManager.hasActiveSession(TEST_TELEGRAM_ID)).thenReturn(true);
        doNothing().when(sessionManager).endSession(TEST_TELEGRAM_ID);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).contains("Program draft cancelled");

        verify(sessionManager).endSession(TEST_TELEGRAM_ID);
    }

    private Update createMockUpdateWithCommand() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        lenient().when(update.hasMessage()).thenReturn(true);
        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.hasText()).thenReturn(true);
        lenient().when(message.getText()).thenReturn("/cancel_program");
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(TEST_TELEGRAM_ID);
        lenient().when(message.getChatId()).thenReturn(TEST_CHAT_ID);

        return update;
    }
}
