package com.example.fitnessbot.integration;

import com.example.fitnessbot.FitnessBotApplication;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.UserRepository;
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
class DatabaseIntegrationTest {

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
    private UserRepository userRepository;

    @Test
    void testDatabaseConnectionAndUserRepository() {
        // Create a new user
        User user = new User();
        user.setTelegramId(123456789L);
        user.setName("Test User");
        user.setWeightKg(75.5);

        // Save the user
        User savedUser = userRepository.save(user);

        // Verify the user was saved
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getTelegramId()).isEqualTo(123456789L);
        assertThat(savedUser.getName()).isEqualTo("Test User");
        assertThat(savedUser.getWeightKg()).isEqualTo(75.5);

        // Retrieve the user
        User retrievedUser = userRepository.findByTelegramId(123456789L).orElse(null);
        assertThat(retrievedUser).isNotNull();
        assertThat(retrievedUser.getName()).isEqualTo("Test User");
    }
}