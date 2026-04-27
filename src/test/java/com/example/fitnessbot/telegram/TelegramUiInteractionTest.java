package com.example.fitnessbot.telegram;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.service.WorkoutService;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import com.example.fitnessbot.telegram.commands.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramUiInteractionTest {

    private static final long USER_ID = 12345;
    private static final long CHAT_ID = 6789;

    @Mock
    private TrainingDayService trainingDayService;

    @Mock
    private ProgramService programService;

    @Mock
    private WorkoutService workoutService;

    private FitnessTelegramBot fitnessTelegramBot;
    private ProgramCreationSessionManager sessionManager;

    @BeforeEach
    void setUp() throws Exception {
        // Create real instances for better testing of UI interactions
        sessionManager = new ProgramCreationSessionManager();
        MenuKeyboardFactory menuKeyboardFactory = new DefaultMenuKeyboardFactory(sessionManager);

        List<CommandHandler> commandHandlers = List.of(
            new StartCommandHandler(menuKeyboardFactory),
            new HelpCommandHandler(new CommandRegistryService(), menuKeyboardFactory),
            new MenuCommandHandler(menuKeyboardFactory),
            new CreateProgramCommandHandler(programService, sessionManager, menuKeyboardFactory),
            new CancelProgramCommandHandler(sessionManager, menuKeyboardFactory),
            new FinishProgramCommandHandler(programService, sessionManager, menuKeyboardFactory),
            new ShowProgramCommandHandler(programService, sessionManager)
        );

        List<CallbackQueryHandler> callbackQueryHandlers = List.of(
            new ShowDayCommandHandler(trainingDayService)
        );

        FitnessTelegramBot bot = new FitnessTelegramBot(
            trainingDayService,
            workoutService,
            sessionManager,
            commandHandlers,
            callbackQueryHandlers,
            new CommandRegistryService(),
            menuKeyboardFactory,
            "test-token",
            "test-username"
        );

        fitnessTelegramBot = spy(bot);
        // Mock the sendTelegramMessage to avoid actual Telegram API calls
        doNothing().when(fitnessTelegramBot).sendTelegramMessage(any(SendMessage.class));
    }

    @Test
    void testMainMenuKeyboardDisplay() throws Exception {
        Update update = createMockUpdateWithCommand("/menu");

        fitnessTelegramBot.onUpdateReceived(update);

        // Capture the sent message to verify keyboard structure
        ArgumentCaptor<SendMessage> captor1 = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(captor1.capture());

        SendMessage sentMessage = captor1.getValue();
        assertNotNull(sentMessage.getReplyMarkup());
        assertTrue(sentMessage.getReplyMarkup() instanceof InlineKeyboardMarkup);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) sentMessage.getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();

        // Verify main menu structure
        assertEquals(2, keyboard.size()); // Create/View row + Help row
        assertEquals(2, keyboard.get(0).size()); // Create Program + View Programs
        assertEquals(1, keyboard.get(1).size()); // Help button

        // Verify button labels and callback data
        InlineKeyboardButton createBtn = keyboard.get(0).get(0);
        assertEquals("Create Program", createBtn.getText());
        assertEquals("create_program", createBtn.getCallbackData());

        InlineKeyboardButton viewBtn = keyboard.get(0).get(1);
        assertEquals("View Programs", viewBtn.getText());
        assertEquals("view_programs", viewBtn.getCallbackData());

        InlineKeyboardButton helpBtn = keyboard.get(1).get(0);
        assertEquals("Help", helpBtn.getText());
        assertEquals("help", helpBtn.getCallbackData());
    }

    @Test
    void testMainMenuWithActiveSession() throws Exception {
        // Simulate an active session
        sessionManager.startSession(USER_ID, new com.example.fitnessbot.model.Program());

        Update update = createMockUpdateWithCommand("/menu");
        fitnessTelegramBot.onUpdateReceived(update);

        // Capture the sent message
        ArgumentCaptor<SendMessage> captor2 = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(captor2.capture());

        SendMessage sentMessage = captor2.getValue();
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) sentMessage.getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();

        // With active session, should show Finish/Cancel buttons instead of Create/View
        assertEquals(2, keyboard.size());
        assertEquals(2, keyboard.get(0).size()); // Finish + Cancel buttons
        assertEquals(1, keyboard.get(1).size()); // Help button

        InlineKeyboardButton finishBtn = keyboard.get(0).get(0);
        assertEquals("Finish Program", finishBtn.getText());
        assertEquals("finish_program", finishBtn.getCallbackData());

        InlineKeyboardButton cancelBtn = keyboard.get(0).get(1);
        assertEquals("Cancel Program", cancelBtn.getText());
        assertEquals("cancel_program", cancelBtn.getCallbackData());
    }

    @Test
    void testMainMenuTransitionsBetweenSessionStates() throws Exception {
        // Test 1: Menu without session
        Update updateWithoutSession = createMockUpdateWithCommand("/menu");
        fitnessTelegramBot.onUpdateReceived(updateWithoutSession);

        ArgumentCaptor<SendMessage> captor3 = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(captor3.capture());

        SendMessage sentMessage1 = captor3.getValue();
        InlineKeyboardMarkup markup1 = (InlineKeyboardMarkup) sentMessage1.getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard1 = markup1.getKeyboard();

        // Should have Create Program button
        boolean hasCreateButton = false;
        for (List<InlineKeyboardButton> row : keyboard1) {
            for (InlineKeyboardButton button : row) {
                if ("Create Program".equals(button.getText())) {
                    hasCreateButton = true;
                    break;
                }
            }
        }
        assertTrue(hasCreateButton);

        // Now start a session
        sessionManager.startSession(USER_ID, new com.example.fitnessbot.model.Program());

        // Test 2: Menu with active session
        Update updateWithSession = createMockUpdateWithCommand("/menu");
        fitnessTelegramBot.onUpdateReceived(updateWithSession);

        ArgumentCaptor<SendMessage> captor4 = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot, times(2)).sendTelegramMessage(captor4.capture());

        List<SendMessage> allMessages = captor4.getAllValues();
        SendMessage sentMessage2 = allMessages.get(1);
        InlineKeyboardMarkup markup2 = (InlineKeyboardMarkup) sentMessage2.getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard2 = markup2.getKeyboard();

        // Should have Finish Program button instead
        boolean hasFinishButton = false;
        for (List<InlineKeyboardButton> row : keyboard2) {
            for (InlineKeyboardButton button : row) {
                if ("Finish Program".equals(button.getText())) {
                    hasFinishButton = true;
                    break;
                }
            }
        }
        assertTrue(hasFinishButton);
    }

    @Test
    void testCommandMenuHidesSessionCommandsWithoutActiveSession() throws Exception {
        Update update = createMockUpdateWithCommand("/");
        fitnessTelegramBot.onUpdateReceived(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(captor.capture());

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) captor.getValue().getReplyMarkup();
        assertFalse(commandKeyboardContains(markup, "/cancel_program"));
        assertFalse(commandKeyboardContains(markup, "/finish_program"));
        assertTrue(commandKeyboardContains(markup, "/create_program"));
    }

    @Test
    void testCommandMenuShowsSessionCommandsWithActiveSession() throws Exception {
        sessionManager.startSession(USER_ID, new com.example.fitnessbot.model.Program());

        Update update = createMockUpdateWithCommand("/");
        fitnessTelegramBot.onUpdateReceived(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(captor.capture());

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) captor.getValue().getReplyMarkup();
        assertTrue(commandKeyboardContains(markup, "/cancel_program"));
        assertTrue(commandKeyboardContains(markup, "/finish_program"));
        assertFalse(commandKeyboardContains(markup, "/create_program"));
    }

    @Test
    void testHelpCallbackQuery() throws Exception {
        Update update = createMockUpdateWithCallbackQuery("help");
        fitnessTelegramBot.onUpdateReceived(update);

        ArgumentCaptor<SendMessage> captor5 = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(captor5.capture());

        SendMessage sentMessage = captor5.getValue();
        assertNotNull(sentMessage);
        assertTrue(sentMessage.getText().contains("Message format I can read"));
    }

    @Test
    void testCreateProgramCallbackQuery() throws Exception {
        com.example.fitnessbot.model.Program program = new com.example.fitnessbot.model.Program();
        program.setId(1L);
        program.setName("My Program");
        when(programService.startProgramCreation(USER_ID, "My Program")).thenReturn(program);

        Update update = createMockUpdateWithCallbackQuery("create_program");
        fitnessTelegramBot.onUpdateReceived(update);

        ArgumentCaptor<SendMessage> captor6 = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(captor6.capture());

        SendMessage sentMessage = captor6.getValue();
        assertNotNull(sentMessage);
        assertTrue(sentMessage.getText().contains("Program draft created"));
        assertTrue(sentMessage.getText().contains("My Program"));
        assertTrue(sessionManager.hasActiveSession(USER_ID));
    }

    @Test
    void testViewProgramsCallbackQuery() throws Exception {
        com.example.fitnessbot.model.Program program = new com.example.fitnessbot.model.Program();
        program.setId(1L);
        program.setName("Strength");
        when(programService.getProgramsForUser(USER_ID)).thenReturn(List.of(program));

        Update update = createMockUpdateWithCallbackQuery("view_programs");
        fitnessTelegramBot.onUpdateReceived(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(captor.capture());

        SendMessage sentMessage = captor.getValue();
        assertNotNull(sentMessage);
        assertTrue(sentMessage.getText().contains("Your Saved Programs"));
        assertTrue(sentMessage.getText().contains("#1 Strength"));
        assertTrue(sentMessage.getReplyMarkup() instanceof InlineKeyboardMarkup);
    }

    @Test
    void testSavedProgramButtonCallbackQuery() throws Exception {
        com.example.fitnessbot.model.Program program = new com.example.fitnessbot.model.Program();
        program.setId(1L);
        program.setName("Strength");

        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setTitle("Upper Body");

        com.example.fitnessbot.model.ProgramTrainingDay programTrainingDay = new com.example.fitnessbot.model.ProgramTrainingDay();
        programTrainingDay.setPosition(1);
        programTrainingDay.setTrainingDay(trainingDay);

        when(programService.getProgramForUser(1L, USER_ID)).thenReturn(java.util.Optional.of(program));
        when(programService.getProgramTrainingDaysForUser(1L, USER_ID)).thenReturn(List.of(programTrainingDay));

        Update update = createMockUpdateWithCallbackQuery("show_program:1");
        fitnessTelegramBot.onUpdateReceived(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot).sendTelegramMessage(captor.capture());

        SendMessage sentMessage = captor.getValue();
        assertNotNull(sentMessage);
        assertTrue(sentMessage.getText().contains("Strength"));
        assertTrue(sentMessage.getText().contains("1. Upper Body"));
    }

    @Test
    void testProgramCreationWorkflowWithForwardedTrainingDays() throws Exception {
        // Start program creation
        com.example.fitnessbot.model.Program program = new com.example.fitnessbot.model.Program();
        program.setId(1L);
        program.setName("My Test Program");
        when(programService.startProgramCreation(USER_ID, "My Test Program")).thenReturn(program);
        when(trainingDayService.processForwardedMessage(eq(USER_ID), anyString()))
                .thenReturn(trainingDay(1L), trainingDay(2L));

        Update createUpdate = createMockUpdateWithCommand("/create_program My Test Program");
        fitnessTelegramBot.onUpdateReceived(createUpdate);

        // Verify session is started
        assertTrue(sessionManager.hasActiveSession(USER_ID));

        // Forward first training day
        Update forwardUpdate1 = createMockForwardedMessage("Upper Body Day:\n\nWarm-up:\n- Band Pull-aparts x20\n- Face pulls x15\n\nMain:\n- Bench press 3 x 8");
        fitnessTelegramBot.onUpdateReceived(forwardUpdate1);

        // Forward second training day
        Update forwardUpdate2 = createMockForwardedMessage("Lower Body Day:\n\nActivation:\n- Hip thrust x12\n- Bulgarian split squat x10\n\nMain:\n- Deadlift 3 x 5");
        fitnessTelegramBot.onUpdateReceived(forwardUpdate2);

        // Finish program
        Update finishUpdate = createMockUpdateWithCommand("/finish_program");
        fitnessTelegramBot.onUpdateReceived(finishUpdate);

        // Verify program was created with both training days
        verify(trainingDayService, times(2)).processForwardedMessage(eq(USER_ID), anyString());
        assertFalse(sessionManager.hasActiveSession(USER_ID));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot, atLeastOnce()).sendTelegramMessage(captor.capture());

        assertTrue(captor.getAllValues().stream().anyMatch(message ->
                message.getText() != null
                        && message.getText().contains("Forward another day, or tap \"Finish Program\"")
                        && message.getText().contains("Finish Program")
                        && message.getReplyMarkup() instanceof InlineKeyboardMarkup
        ));
    }

    @Test
    void testProgramCreationButtonWorkflowWithForwardedTrainingDays() throws Exception {
        com.example.fitnessbot.model.Program program = new com.example.fitnessbot.model.Program();
        program.setId(1L);
        program.setName("My Program");
        when(programService.startProgramCreation(USER_ID, "My Program")).thenReturn(program);
        when(trainingDayService.processForwardedMessage(eq(USER_ID), anyString()))
                .thenReturn(trainingDay(1L), trainingDay(2L));

        Update createUpdate = createMockUpdateWithCallbackQuery("create_program");
        fitnessTelegramBot.onUpdateReceived(createUpdate);

        assertTrue(sessionManager.hasActiveSession(USER_ID));

        Update forwardUpdate1 = createMockForwardedMessage("AI parsed day 1");
        fitnessTelegramBot.onUpdateReceived(forwardUpdate1);

        Update forwardUpdate2 = createMockForwardedMessage("AI parsed day 2");
        fitnessTelegramBot.onUpdateReceived(forwardUpdate2);

        Update finishUpdate = createMockUpdateWithCommand("/finish_program");
        fitnessTelegramBot.onUpdateReceived(finishUpdate);

        verify(trainingDayService, times(2)).processForwardedMessage(eq(USER_ID), anyString());
        verify(programService).addTrainingDayToProgram(1L, 1L, 1);
        verify(programService).addTrainingDayToProgram(1L, 2L, 2);
        assertFalse(sessionManager.hasActiveSession(USER_ID));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot, atLeastOnce()).sendTelegramMessage(captor.capture());

        assertTrue(captor.getAllValues().stream().anyMatch(message ->
                message.getText() != null
                        && message.getText().contains("Forward another day, or tap \"Finish Program\"")
        ));
        assertTrue(captor.getAllValues().stream().anyMatch(message ->
                message.getText() != null
                        && message.getText().contains("Program \"My Program\" is ready.")
        ));
    }

    @Test
    void testShowDayCallbackQueryWithNoTrainingDays() throws Exception {
        Update update = createMockUpdateWithCallbackQuery("show_day_1");
        fitnessTelegramBot.onUpdateReceived(update);

        ArgumentCaptor<SendMessage> captor7 = ArgumentCaptor.forClass(SendMessage.class);
        verify(fitnessTelegramBot, atLeastOnce()).sendTelegramMessage(captor7.capture());

        List<SendMessage> sentMessages = captor7.getAllValues();
        // Should have at least one message (error message since no training days)
        assertTrue(sentMessages.size() >= 1);
        assertTrue(sentMessages.get(0).getText().contains("Training day not found"));
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

    private Update createMockForwardedMessage(String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        lenient().when(update.hasMessage()).thenReturn(true);
        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.hasText()).thenReturn(true);
        lenient().when(message.getText()).thenReturn(text);
        lenient().when(message.getForwardFrom()).thenReturn(user);
        lenient().when(message.getChatId()).thenReturn(CHAT_ID);
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(USER_ID);

        return update;
    }

    private TrainingDay trainingDay(Long id) {
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(id);
        trainingDay.setExercises(List.of());
        User user = new User();
        user.setTelegramId(USER_ID);
        trainingDay.setUser(user);
        return trainingDay;
    }

    private boolean commandKeyboardContains(InlineKeyboardMarkup markup, String command) {
        return markup.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(button -> command.equals(button.getText()));
    }
}
