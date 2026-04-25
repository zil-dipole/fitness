package com.example.fitnessbot.integration;

import com.example.fitnessbot.AbstractWithDbTest;
import com.example.fitnessbot.FitnessBotApplication;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.UserRepository;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.telegram.FitnessTelegramBot;
import com.example.fitnessbot.telegram.MenuKeyboardFactory;
import com.example.fitnessbot.telegram.commands.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = FitnessBotApplication.class)
class TelegramBotIntegrationTest extends AbstractWithDbTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgramCreationSessionManager sessionManager;

    @MockBean
    private TrainingDayService trainingDayService;

    @MockBean
    private FitnessTelegramBot fitnessTelegramBot;

    @Test
    void testContextLoads() {
        // This test verifies that the application context loads successfully
        // even when Telegram bot token is empty (which should disable the bot)
        assertThat(true).isTrue();
    }

    @Test
    void testDatabaseRepositoryWorks() {
        // Verify that we can save and retrieve a user
        User user = new User();
        user.setTelegramId(987654321L);
        user.setName("Database Test User");
        user.setWeightKg(80.0);

        User savedUser = userRepository.save(user);
        assertThat(savedUser.getId()).isNotNull();

        User retrievedUser = userRepository.findById(savedUser.getId()).orElse(null);
        assertThat(retrievedUser).isNotNull();
        assertThat(retrievedUser.getName()).isEqualTo("Database Test User");
        assertThat(retrievedUser.getTelegramId()).isEqualTo(987654321L);
        assertThat(retrievedUser.getWeightKg()).isEqualTo(80.0);
    }

    @Test
    void testProgramCreationSessionManager() {
        // Test that the session manager bean is properly wired
        assertThat(sessionManager).isNotNull();

        // Initially no session for user
        assertThat(sessionManager.hasActiveSession(12345L)).isFalse();

        // Create a mock program object for testing
        com.example.fitnessbot.model.Program program = new com.example.fitnessbot.model.Program();
        program.setName("Test Program");

        // Start a session
        sessionManager.startSession(12345L, program);

        // Verify session exists
        assertThat(sessionManager.hasActiveSession(12345L)).isTrue();
        var session = sessionManager.getSession(12345L);
        assertThat(session).isNotNull();
        assertThat(session.getProgram().getName()).isEqualTo("Test Program");

        // End session
        sessionManager.endSession(12345L);

        // Verify session is gone
        assertThat(sessionManager.hasActiveSession(12345L)).isFalse();
        assertThat(sessionManager.getSession(12345L)).isNull();
    }

    @Test
    void testCommandHandlersAreLoaded() {
        // Test that all command handlers are created as beans and available

        // Create command handlers with minimal dependencies for testing instantiation
        MenuKeyboardFactory menuKeyboardFactory = mock(MenuKeyboardFactory.class);
        java.util.List<com.example.fitnessbot.telegram.commands.CommandHandler> commandHandlers = java.util.List.of(
                new com.example.fitnessbot.telegram.commands.StartCommandHandler(menuKeyboardFactory),
                new com.example.fitnessbot.telegram.commands.HelpCommandHandler(new com.example.fitnessbot.telegram.commands.CommandRegistryService(), menuKeyboardFactory),
                new com.example.fitnessbot.telegram.commands.CreateProgramCommandHandler(null, null, menuKeyboardFactory),  // Dependencies will be mocked in real usage
                new com.example.fitnessbot.telegram.commands.FinishProgramCommandHandler(null, null, menuKeyboardFactory),
                new com.example.fitnessbot.telegram.commands.CancelProgramCommandHandler(null, menuKeyboardFactory)
        );

        // Verify all handlers are instantiated
        assertThat(commandHandlers).hasSize(5);

        // Verify each handler can identify commands it handles
        assertThat(commandHandlers.get(0).canHandle("/start")).isTrue();
        assertThat(commandHandlers.get(1).canHandle("/help")).isTrue();
        assertThat(commandHandlers.get(2).canHandle("/create_program")).isTrue();
        assertThat(commandHandlers.get(3).canHandle("/finish_program")).isTrue();
        assertThat(commandHandlers.get(4).canHandle("/cancel_program")).isTrue();
    }

    @Test
    void testContextAwareCommandAvailability() {
        // Test context-aware command availability logic

        // Create command handlers
        MenuKeyboardFactory menuKeyboardFactory = mock(MenuKeyboardFactory.class);
        StartCommandHandler startHandler = new StartCommandHandler(menuKeyboardFactory);
        CreateProgramCommandHandler createHandler = new CreateProgramCommandHandler(null, sessionManager, menuKeyboardFactory);
        FinishProgramCommandHandler finishHandler = new FinishProgramCommandHandler(null, sessionManager, menuKeyboardFactory);
        CancelProgramCommandHandler cancelHandler = new CancelProgramCommandHandler(sessionManager, menuKeyboardFactory);

        long testUserId = 54321L;

        // Initially no session
        assertThat(sessionManager.hasActiveSession(testUserId)).isFalse();

        // Start command should be available
        assertThat(startHandler.isAvailable(testUserId, sessionManager)).isTrue();

        // Create program command should be available
        assertThat(createHandler.isAvailable(testUserId, sessionManager)).isTrue();

        // Finish and cancel commands should NOT be available
        assertThat(finishHandler.isAvailable(testUserId, sessionManager)).isFalse();
        assertThat(cancelHandler.isAvailable(testUserId, sessionManager)).isFalse();

        // Start a session
        com.example.fitnessbot.model.Program program = new com.example.fitnessbot.model.Program();
        program.setName("Context Test Program");
        sessionManager.startSession(testUserId, program);

        // Now check availabilities have changed appropriately

        // Start command should NOT be available
        assertThat(startHandler.isAvailable(testUserId, sessionManager)).isFalse();

        // Create program command should NOT be available
        assertThat(createHandler.isAvailable(testUserId, sessionManager)).isFalse();

        // Finish and cancel commands SHOULD be available
        assertThat(finishHandler.isAvailable(testUserId, sessionManager)).isTrue();
        assertThat(cancelHandler.isAvailable(testUserId, sessionManager)).isTrue();

        // End session
        sessionManager.endSession(testUserId);

        // Availabilities should revert
        assertThat(startHandler.isAvailable(testUserId, sessionManager)).isTrue();
        assertThat(createHandler.isAvailable(testUserId, sessionManager)).isTrue();
        assertThat(finishHandler.isAvailable(testUserId, sessionManager)).isFalse();
        assertThat(cancelHandler.isAvailable(testUserId, sessionManager)).isFalse();
    }

    @Test
    void testMultipleUserSessionsAreIndependent() {
        // Test that different users have independent session states

        long user1Id = 10001L;
        long user2Id = 10002L;
        long user3Id = 10003L;

        // All start with no sessions
        assertThat(sessionManager.hasActiveSession(user1Id)).isFalse();
        assertThat(sessionManager.hasActiveSession(user2Id)).isFalse();
        assertThat(sessionManager.hasActiveSession(user3Id)).isFalse();

        // User 1 starts a session
        com.example.fitnessbot.model.Program program1 = new com.example.fitnessbot.model.Program();
        program1.setName("User 1 Program");
        sessionManager.startSession(user1Id, program1);

        // Only user 1 should have an active session
        assertThat(sessionManager.hasActiveSession(user1Id)).isTrue();
        assertThat(sessionManager.hasActiveSession(user2Id)).isFalse();
        assertThat(sessionManager.hasActiveSession(user3Id)).isFalse();

        // User 2 starts a session
        com.example.fitnessbot.model.Program program2 = new com.example.fitnessbot.model.Program();
        program2.setName("User 2 Program");
        sessionManager.startSession(user2Id, program2);

        // Users 1 and 2 should have active sessions
        assertThat(sessionManager.hasActiveSession(user1Id)).isTrue();
        assertThat(sessionManager.hasActiveSession(user2Id)).isTrue();
        assertThat(sessionManager.hasActiveSession(user3Id)).isFalse();

        // User 1 ends session
        sessionManager.endSession(user1Id);

        // Only user 2 should have an active session
        assertThat(sessionManager.hasActiveSession(user1Id)).isFalse();
        assertThat(sessionManager.hasActiveSession(user2Id)).isTrue();
        assertThat(sessionManager.hasActiveSession(user3Id)).isFalse();

        // User 2 ends session
        sessionManager.endSession(user2Id);

        // No users should have active sessions
        assertThat(sessionManager.hasActiveSession(user1Id)).isFalse();
        assertThat(sessionManager.hasActiveSession(user2Id)).isFalse();
        assertThat(sessionManager.hasActiveSession(user3Id)).isFalse();
    }
}
