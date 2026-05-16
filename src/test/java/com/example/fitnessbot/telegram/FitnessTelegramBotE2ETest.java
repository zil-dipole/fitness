package com.example.fitnessbot.telegram;

import com.example.fitnessbot.AbstractWithDbTest;
import com.example.fitnessbot.FitnessBotApplication;
import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.ProgramRepository;
import com.example.fitnessbot.repository.UserRepository;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.TelegramBotsApi;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = FitnessBotApplication.class,
        properties = {
                "telegram.bot.token=test-token",
                "telegram.bot.username=test-bot"
        }
)
class FitnessTelegramBotE2ETest extends AbstractWithDbTest {

    private static final long TELEGRAM_USER_ID = 920001L;
    private static final long CHAT_ID = 880001L;

    static {
        System.setProperty("test.profile", "true");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramCreationSessionManager sessionManager;

    @SpyBean
    private FitnessTelegramBot fitnessTelegramBot;

    @MockBean(name = "telegramBotsApi")
    private TelegramBotsApi telegramBotsApi;

    @BeforeEach
    void setUp() throws Exception {
        sessionManager.endSession(TELEGRAM_USER_ID);
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));
        clearInvocations(fitnessTelegramBot);
    }

    @Test
    void createCancelAndShowProgramFlowUsesRealBotAndPersistence() throws Exception {
        fitnessTelegramBot.onUpdateReceived(commandUpdate("/create_program E2E Strength"));

        ArgumentCaptor<SendMessage> createCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(createCaptor.capture());

        SendMessage createdDraftMessage = createCaptor.getValue();
        assertThat(createdDraftMessage.getText()).contains("E2E Strength");
        assertThat(sessionManager.hasActiveSession(TELEGRAM_USER_ID)).isTrue();

        User persistedUser = userRepository.findByTelegramId(TELEGRAM_USER_ID).orElseThrow();
        assertThat(persistedUser.getTelegramUsername()).isEqualTo("e2e_user");
        List<Program> persistedPrograms = programRepository.findByUserId(persistedUser.getId());
        assertThat(persistedPrograms).hasSize(1);
        Program createdProgram = persistedPrograms.getFirst();
        assertThat(createdProgram.getName()).isEqualTo("E2E Strength");

        clearInvocations(fitnessTelegramBot);

        fitnessTelegramBot.onUpdateReceived(commandUpdate("/cancel_program"));

        ArgumentCaptor<SendMessage> cancelCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(cancelCaptor.capture());

        SendMessage cancelMessage = cancelCaptor.getValue();
        assertThat(cancelMessage.getText()).containsIgnoringCase("cancel");
        assertThat(sessionManager.hasActiveSession(TELEGRAM_USER_ID)).isFalse();

        clearInvocations(fitnessTelegramBot);

        fitnessTelegramBot.onUpdateReceived(commandUpdate("/show_program"));

        ArgumentCaptor<SendMessage> showListCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(showListCaptor.capture());

        SendMessage programListMessage = showListCaptor.getValue();
        assertThat(programListMessage.getText()).contains("E2E Strength");
        assertThat(programListMessage.getReplyMarkup()).isInstanceOf(InlineKeyboardMarkup.class);

        InlineKeyboardMarkup programListMarkup = (InlineKeyboardMarkup) programListMessage.getReplyMarkup();
        InlineKeyboardButton openProgramButton = programListMarkup.getKeyboard().getFirst().getFirst();
        assertThat(openProgramButton.getText()).contains("E2E Strength");
        assertThat(openProgramButton.getCallbackData()).isEqualTo("show_program:" + createdProgram.getId());

        clearInvocations(fitnessTelegramBot);

        fitnessTelegramBot.onUpdateReceived(callbackUpdate(openProgramButton.getCallbackData()));

        ArgumentCaptor<SendMessage> showDetailsCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot, times(1)).sendTelegramMessage(showDetailsCaptor.capture());

        SendMessage programDetailsMessage = showDetailsCaptor.getValue();
        assertThat(programDetailsMessage.getText()).contains("E2E Strength");
        assertThat(programDetailsMessage.getText()).contains("No training days are linked to this program yet.");
    }

    private Update commandUpdate(String text) {
        Update update = new Update();
        Message message = baseMessage();
        message.setText(text);
        update.setMessage(message);
        return update;
    }

    private Update callbackUpdate(String callbackData) {
        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setId("cb-" + callbackData);
        callbackQuery.setData(callbackData);
        callbackQuery.setFrom(telegramUser());
        callbackQuery.setMessage(baseMessage());
        update.setCallbackQuery(callbackQuery);
        return update;
    }

    private Message baseMessage() {
        Message message = new Message();
        message.setFrom(telegramUser());
        message.setChat(chat());
        return message;
    }

    private org.telegram.telegrambots.meta.api.objects.User telegramUser() {
        org.telegram.telegrambots.meta.api.objects.User user = new org.telegram.telegrambots.meta.api.objects.User();
        user.setId(TELEGRAM_USER_ID);
        user.setFirstName("E2E");
        user.setUserName("E2E_User");
        user.setIsBot(false);
        return user;
    }

    private Chat chat() {
        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        return chat;
    }
}
