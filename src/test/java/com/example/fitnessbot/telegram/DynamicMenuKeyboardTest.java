package com.example.fitnessbot.telegram;

import com.example.fitnessbot.AbstractWithDbTest;
import com.example.fitnessbot.FitnessBotApplication;
import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.telegram.commands.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = FitnessBotApplication.class)
@TestPropertySource(properties = {
    "spring.liquibase.enabled=false",
    "test.profile=true"
})
class DynamicMenuKeyboardTest extends AbstractWithDbTest {

    @Autowired
    private ProgramCreationSessionManager sessionManager;

    @MockBean
    private TrainingDayService trainingDayService;

    private FitnessTelegramBot bot;
    private final Long TEST_USER_ID = 12345L;

    @BeforeEach
    void setUp() {
        MenuKeyboardFactory menuKeyboardFactory = mock(MenuKeyboardFactory.class);
        
        List<CommandHandler> commandHandlers = List.of(
            new StartCommandHandler(menuKeyboardFactory),
            new HelpCommandHandler(new CommandRegistryService(), menuKeyboardFactory),
            new MenuCommandHandler(menuKeyboardFactory),
            new CreateProgramCommandHandler(null, sessionManager, menuKeyboardFactory),
            new CancelProgramCommandHandler(sessionManager, menuKeyboardFactory),
            new FinishProgramCommandHandler(null, sessionManager, menuKeyboardFactory)
        );

        bot = new FitnessTelegramBot(
            trainingDayService,
            sessionManager,
            commandHandlers,
            List.of(),
            new CommandRegistryService(),
            menuKeyboardFactory,
            "test-token",
            "test-username"
        );
        
        // Mock the send method to prevent actual Telegram calls
        doNothing().when(trainingDayService).processForwardedMessage(any(), any());
    }

    @Test
    void testMainMenuWithoutActiveSession() {
        // Ensure no active session
        sessionManager.endSession(TEST_USER_ID);

        // Send /menu command and capture the response
        Update menuUpdate = createMenuUpdate();
        AtomicReference<SendMessage> response = new AtomicReference<>();
        
        doAnswer(invocation -> {
            response.set(invocation.getArgument(0));
            return null;
        }).when(trainingDayService).processForwardedMessage(any(), any());

        bot.onUpdateReceived(menuUpdate);

        assertThat(response).isNotNull();
        assertThat(response.get().getText()).contains("Welcome to Fitness Bot");

        // Verify menu structure without active session
        // We can't directly test the private method, but we can verify a response was sent
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.get().getReplyMarkup();
        assertThat(markup).isNotNull();
    }

    @Test
    void testMainMenuWithActiveSession() {
        // Start a session
        Program program = new Program();
        program.setName("Test Program");
        sessionManager.startSession(TEST_USER_ID, program);

        // Send /menu command and capture the response
        Update menuUpdate = createMenuUpdate();
        AtomicReference<SendMessage> response = new AtomicReference<>();
        
        doAnswer(invocation -> {
            response.set(invocation.getArgument(0));
            return null;
        }).when(trainingDayService).processForwardedMessage(any(), any());

        bot.onUpdateReceived(menuUpdate);

        assertThat(response).isNotNull();
        assertThat(response.get().getText()).contains("Welcome to Fitness Bot");

        // Verify menu structure with active session
        // We can't directly test the private method, but we can verify a response was sent
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.get().getReplyMarkup();
        assertThat(markup).isNotNull();
    }

    @Test
    void testMainMenuTransitionsBetweenStates() {
        // Test transition: No session -> Session -> No session

        // 1. Without session
        sessionManager.endSession(TEST_USER_ID);
        
        Update menuUpdate1 = createMenuUpdate();
        AtomicReference<SendMessage> response1 = new AtomicReference<>();
        
        doAnswer(invocation -> {
            response1.set(invocation.getArgument(0));
            return null;
        }).when(trainingDayService).processForwardedMessage(any(), any());

        bot.onUpdateReceived(menuUpdate1);

        assertThat(response1).isNotNull();
        InlineKeyboardMarkup markup1 = (InlineKeyboardMarkup) response1.get().getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard1 = markup1.getKeyboard();

        // Check structure without session
        assertThat(keyboard1).hasSize(2);
        List<InlineKeyboardButton> firstRow1 = keyboard1.getFirst();
        assertThat(firstRow1.getFirst().getText()).isEqualTo("Create Program");

        // 2. With session
        Program program = new Program();
        program.setName("Test Program");
        sessionManager.startSession(TEST_USER_ID, program);

        Update menuUpdate2 = createMenuUpdate();
        AtomicReference<SendMessage> response2 = new AtomicReference<>();
        
        doAnswer(invocation -> {
            response2.set(invocation.getArgument(0));
            return null;
        }).when(trainingDayService).processForwardedMessage(any(), any());

        bot.onUpdateReceived(menuUpdate2);

        assertThat(response2).isNotNull();
        InlineKeyboardMarkup markup2 = (InlineKeyboardMarkup) response2.get().getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard2 = markup2.getKeyboard();

        // Check structure with session
        assertThat(keyboard2).hasSize(2);
        List<InlineKeyboardButton> firstRow2 = keyboard2.getFirst();
        assertThat(firstRow2.getFirst().getText()).isEqualTo("Finish Program");

        // 3. Back to no session
        sessionManager.endSession(TEST_USER_ID);

        Update menuUpdate3 = createMenuUpdate();
        AtomicReference<SendMessage> response3 = new AtomicReference<>();
        
        doAnswer(invocation -> {
            response3.set(invocation.getArgument(0));
            return null;
        }).when(trainingDayService).processForwardedMessage(any(), any());

        bot.onUpdateReceived(menuUpdate3);

        assertThat(response3).isNotNull();
        InlineKeyboardMarkup markup3 = (InlineKeyboardMarkup) response3.get().getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard3 = markup3.getKeyboard();

        // Check structure back to without session
        assertThat(keyboard3).hasSize(2);
        List<InlineKeyboardButton> firstRow3 = keyboard3.getFirst();
        assertThat(firstRow3.getFirst().getText()).isEqualTo("Create Program");
    }

    // Helper method to create menu update
    private Update createMenuUpdate() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        lenient().when(update.hasMessage()).thenReturn(true);
        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.hasText()).thenReturn(true);
        lenient().when(message.getText()).thenReturn("/menu");
        Long TEST_CHAT_ID = 67890L;
        lenient().when(message.getChatId()).thenReturn(TEST_CHAT_ID);
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(TEST_USER_ID);

        return update;
    }
}