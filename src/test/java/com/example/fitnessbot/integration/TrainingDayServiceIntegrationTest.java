package com.example.fitnessbot.integration;

import com.example.fitnessbot.FitnessBotApplication;
import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.UserRepository;
import com.example.fitnessbot.service.TrainingDayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = FitnessBotApplication.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TrainingDayServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("fitness_bot_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Autowired
    private TrainingDayService trainingDayService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testProcessForwardedMessage() {
        // Create a test user for this specific test
        User testUser = new User();
        testUser.setTelegramId(123456789L);
        testUser.setName("Test User");
        testUser.setWeightKg(75.5);
        testUser = userRepository.save(testUser);

        String rawText = """
                Треня 3:

                Активация разминка:
                - Гандболка с выпадами х20 (с видео) https://www.youtube.com/watch?v=example1
                - Пуловер лёжа х15 (с видео) https://www.youtube.com/watch?v=example2

                Основная часть:
                - Жим штанги лёжа 3 x 6 (70 кг)
                - Жим гантелей сидя 3 x 8 (25 кг)
                """;

        // Process the forwarded message
        TrainingDay result = trainingDayService.processForwardedMessage(testUser.getTelegramId(), rawText);

        // Verify the result is not null anymore
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getUser()).isNotNull();
        assertThat(result.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(result.getTitle()).isEqualTo("Треня 3:");
        assertThat(result.getRawText()).isEqualTo(rawText);
        assertThat(result.getExercises()).isNotEmpty();
        assertThat(result.getExercises()).hasSize(4);

        // Check first exercise
        Exercise ex1 = result.getExercises().get(0);
        assertThat(ex1.getSection()).isEqualTo("Активация разминка");
        assertThat(ex1.getName()).isEqualTo("Гандболка с выпадами х20"); // The name includes the "х20" part because the parser doesn't separate it yet
        assertThat(ex1.getPosition()).isEqualTo(0);
        assertThat(ex1.getVideoUrls()).hasSize(1);
        assertThat(ex1.getVideoUrls().get(0)).contains("youtube.com");

        // Check second exercise
        Exercise ex2 = result.getExercises().get(1);
        assertThat(ex2.getSection()).isEqualTo("Активация разминка");
        assertThat(ex2.getName()).isEqualTo("Пуловер лёжа х15");
        assertThat(ex2.getPosition()).isEqualTo(1);

        // Check third exercise
        Exercise ex3 = result.getExercises().get(2);
        assertThat(ex3.getSection()).isEqualTo("Основная часть");
        assertThat(ex3.getName()).isEqualTo("Жим штанги лёжа");
        assertThat(ex3.getSets()).isEqualTo(3);
        assertThat(ex3.getRepsOrDuration()).isEqualTo("6");
        assertThat(ex3.getNotes()).isEqualTo("(70 кг)");
        assertThat(ex3.getPosition()).isEqualTo(2);
    }

    @Test
    void testDatabaseConnection() {
        // Verify that we can save and retrieve a user
        User user = new User();
        user.setTelegramId(987654321L); // Different Telegram ID to avoid constraint violation
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