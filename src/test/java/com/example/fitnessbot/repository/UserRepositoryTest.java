package com.example.fitnessbot.repository;

import com.example.fitnessbot.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:15:///databasename",
    "spring.liquibase.enabled=true"
})
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testSaveAndFindUser() {
        // Create a new user
        User user = new User();
        user.setTelegramId(123456789L);
        user.setName("John Doe");
        user.setWeightKg(75.5);

        // Save the user
        User savedUser = userRepository.save(user);
        
        // Verify the user was saved
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getTelegramId()).isEqualTo(123456789L);
        assertThat(savedUser.getName()).isEqualTo("John Doe");
        assertThat(savedUser.getWeightKg()).isEqualTo(75.5);
        
        // Find the user by ID
        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getName()).isEqualTo("John Doe");
    }

    @Test
    void testFindByTelegramId() {
        // Create a new user
        User user = new User();
        user.setTelegramId(987654321L);
        user.setName("Jane Smith");
        user.setWeightKg(65.0);
        
        // Save the user
        entityManager.persistAndFlush(user);
        
        // Find the user by Telegram ID
        Optional<User> foundUser = userRepository.findByTelegramId(987654321L);
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getName()).isEqualTo("Jane Smith");
        assertThat(foundUser.get().getWeightKg()).isEqualTo(65.0);
    }

    @Test
    void testFindByNonExistentTelegramId() {
        // Try to find a user with a non-existent Telegram ID
        Optional<User> foundUser = userRepository.findByTelegramId(111111111L);
        assertThat(foundUser).isNotPresent();
    }
}