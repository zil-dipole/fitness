package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.telegram.DefaultMenuKeyboardFactory;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteProgramCallbackHandlerTest {

    private static final Long TEST_TELEGRAM_ID = 12345L;
    private static final Long TEST_CHAT_ID = 6789L;

    @Mock
    private ProgramService programService;

    private DeleteProgramCallbackHandler handler;

    @BeforeEach
    void setUp() {
        MenuKeyboardFactory menuKeyboardFactory = new DefaultMenuKeyboardFactory(new ProgramCreationSessionManager());
        handler = new DeleteProgramCallbackHandler(programService, menuKeyboardFactory);
    }

    @Test
    void testCanHandle() {
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        when(callbackQuery.getData()).thenReturn("delete_program:1");

        assertThat(handler.canHandle(callbackQuery)).isTrue();
    }

    @Test
    void testHandleSuccess() {
        Update update = createUpdate("delete_program:1");
        when(programService.deleteProgramForUser(1L, TEST_TELEGRAM_ID)).thenReturn(true);

        SendMessage response = handler.handle(update);

        assertThat(response.getChatId()).isEqualTo(String.valueOf(TEST_CHAT_ID));
        assertThat(response.getText()).isEqualTo("Program deleted.");
        assertThat(response.getReplyMarkup()).isNotNull();
    }

    @Test
    void testHandleNotFound() {
        Update update = createUpdate("delete_program:1");
        when(programService.deleteProgramForUser(1L, TEST_TELEGRAM_ID)).thenReturn(false);

        SendMessage response = handler.handle(update);

        assertThat(response.getText()).isEqualTo("Program not found.");
    }

    private Update createUpdate(String callbackData) {
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(callbackQuery.getFrom()).thenReturn(user);
        when(message.getChatId()).thenReturn(TEST_CHAT_ID);
        when(user.getId()).thenReturn(TEST_TELEGRAM_ID);
        return update;
    }
}
