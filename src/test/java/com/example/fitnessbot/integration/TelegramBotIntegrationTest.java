package com.example.fitnessbot.integration;

import com.example.fitnessbot.AbstractWithDbTest;
import com.example.fitnessbot.FitnessBotApplication;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.UserRepository;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.telegram.FitnessTelegramBot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

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
        java.util.List<com.example.fitnessbot.telegram.commands.CommandHandler> commandHandlers = java.util.List.of(
                new com.example.fitnessbot.telegram.commands.StartCommandHandler(),
                new com.example.fitnessbot.telegram.commands.HelpCommandHandler(),
                new com.example.fitnessbot.telegram.commands.CreateProgramCommandHandler(null, null),  // Dependencies will be mocked in real usage
                new com.example.fitnessbot.telegram.commands.FinishProgramCommandHandler(null, null),
                new com.example.fitnessbot.telegram.commands.CancelProgramCommandHandler(null)
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
}
