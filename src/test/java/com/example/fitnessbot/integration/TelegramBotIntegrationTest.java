package com.example.fitnessbot.integration;

import com.example.fitnessbot.FitnessBotApplication;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.UserRepository;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.telegram.FitnessTelegramBot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = FitnessBotApplication.class)
@Testcontainers
class TelegramBotIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("fitness_bot_telegram_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
        // Disable Telegram bot in tests by setting empty token
        registry.add("telegram.bot.token", () -> "");
    }

    @Autowired
    private UserRepository userRepository;

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
}
