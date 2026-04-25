package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
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
class FinishProgramCommandHandlerTest {
    
    private static final Long TEST_TELEGRAM_ID = 12345L;
    private static final Long TEST_CHAT_ID = 6789L;

    @Mock
    private ProgramService programService;

    @Mock
    private ProgramCreationSessionManager sessionManager;

    private FinishProgramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FinishProgramCommandHandler(programService, sessionManager, mock(MenuKeyboardFactory.class));
    }

    @Test
    void testCanHandle() {
        assertThat(handler.canHandle("/finish_program")).isTrue();
        assertThat(handler.canHandle("/start")).isFalse();
        assertThat(handler.canHandle("/help")).isFalse();
        assertThat(handler.canHandle("/create_program")).isFalse();
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
        Update update = createMockUpdate();
        SendMessage response = handler.handleUnavailable(update);

        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).isEqualTo("You don't have an active program creation session. Start one with /create_program <program_name>");
    }

    @Test
    void testHandleWithoutActiveSession() {
        Update update = createMockUpdate();
        when(sessionManager.hasActiveSession(TEST_TELEGRAM_ID)).thenReturn(false);

        SendMessage response = handler.handle(update);

        assertThat(response).isNotNull();
        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).isEqualTo("You don't have an active program creation session. Start one with /create_program <program_name>");

        verify(sessionManager).hasActiveSession(TEST_TELEGRAM_ID);
        verifyNoMoreInteractions(sessionManager);
    }

    private Update createMockUpdate() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.getText()).thenReturn("/finish_program");
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(TEST_TELEGRAM_ID);
        lenient().when(message.getChatId()).thenReturn(TEST_CHAT_ID);

        return update;
    }
}