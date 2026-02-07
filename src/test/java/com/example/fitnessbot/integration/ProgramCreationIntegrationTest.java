package com.example.fitnessbot.integration;

import com.example.fitnessbot.AbstractWithDbTest;
import com.example.fitnessbot.FitnessBotApplication;
import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.ProgramTrainingDay;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.*;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.TrainingDayService;
import com.example.fitnessbot.telegram.commands.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = FitnessBotApplication.class)
class ProgramCreationIntegrationTest extends AbstractWithDbTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrainingDayRepository trainingDayRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramTrainingDayRepository programTrainingDayRepository;

    @Autowired
    private ProgramCreationSessionManager sessionManager;

    @MockBean
    private TrainingDayService trainingDayService;

    @Test
    void testProgramCreationFlow() {
        // 1. Create a user
        User user = new User();
        user.setTelegramId(12345L);
        user.setName("Test User");
        user.setWeightKg(75.0);
        user.setCreatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);
        assertThat(savedUser.getId()).isNotNull();

        // 2. Create training days that will be added to the program
        TrainingDay trainingDay1 = new TrainingDay();
        trainingDay1.setUser(savedUser);
        trainingDay1.setTitle("Upper Body");
        trainingDay1.setRawText("Sample training day 1");
        trainingDay1.setCreatedAt(LocalDateTime.now());
        TrainingDay savedTrainingDay1 = trainingDayRepository.save(trainingDay1);
        assertThat(savedTrainingDay1.getId()).isNotNull();

        TrainingDay trainingDay2 = new TrainingDay();
        trainingDay2.setUser(savedUser);
        trainingDay2.setTitle("Lower Body");
        trainingDay2.setRawText("Sample training day 2");
        trainingDay2.setCreatedAt(LocalDateTime.now());
        TrainingDay savedTrainingDay2 = trainingDayRepository.save(trainingDay2);
        assertThat(savedTrainingDay2.getId()).isNotNull();

        // 3. Create exercises for the training days
        Exercise exercise1 = new Exercise();
        exercise1.setTrainingDay(savedTrainingDay1);
        exercise1.setName("Bench Press");
        exercise1.setSets(3);
        exercise1.setRepsOrDuration("10");
        exercise1.setPosition(1);
        exercise1.setSection("Upper Body");
        exerciseRepository.save(exercise1);

        Exercise exercise2 = new Exercise();
        exercise2.setTrainingDay(savedTrainingDay2);
        exercise2.setName("Squats");
        exercise2.setSets(3);
        exercise2.setRepsOrDuration("12");
        exercise2.setPosition(1);
        exercise2.setSection("Lower Body");
        exerciseRepository.save(exercise2);

        // 4. Mock the training day service to return our training days when processing messages
        when(trainingDayService.processForwardedMessage(anyLong(), any(String.class)))
                .thenReturn(savedTrainingDay1) // First call
                .thenReturn(savedTrainingDay2); // Second call

        // 5. Test program creation session lifecycle
        // Initially no session
        assertThat(sessionManager.hasActiveSession(12345L)).isFalse();

        // Start session
        com.example.fitnessbot.model.Program program = new com.example.fitnessbot.model.Program();
        program.setUser(savedUser);
        program.setName("My Test Program");
        sessionManager.startSession(12345L, program);

        // Session should now exist
        assertThat(sessionManager.hasActiveSession(12345L)).isTrue();
        var session = sessionManager.getSession(12345L);
        assertThat(session).isNotNull();
        assertThat(session.getProgram().getName()).isEqualTo("My Test Program");
        assertThat(session.getTrainingDaysCount()).isEqualTo(0);

        // Add training days to session
        session.addTrainingDay(savedTrainingDay1);
        session.addTrainingDay(savedTrainingDay2);
        assertThat(session.getTrainingDaysCount()).isEqualTo(2);

        // End session
        sessionManager.endSession(12345L);
        assertThat(sessionManager.hasActiveSession(12345L)).isFalse();
    }

    @Test
    void testProgramRepositoryOperations() {
        // 1. Create a user
        User user = new User();
        user.setTelegramId(54321L);
        user.setName("Repo Test User");
        user.setWeightKg(80.0);
        user.setCreatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);
        assertThat(savedUser.getId()).isNotNull();

        // 2. Create a program
        com.example.fitnessbot.model.Program program = new com.example.fitnessbot.model.Program();
        program.setUser(savedUser);
        program.setName("Repository Test Program");
        com.example.fitnessbot.model.Program savedProgram = programRepository.save(program);
        assertThat(savedProgram.getId()).isNotNull();

        // 3. Retrieve program
        List<com.example.fitnessbot.model.Program> userPrograms = programRepository.findByUserId(savedUser.getId());
        assertThat(userPrograms).hasSize(1);
        assertThat(userPrograms.getFirst().getName()).isEqualTo("Repository Test Program");

        // 4. Create training days
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setUser(savedUser);
        trainingDay.setTitle("Test Day");
        trainingDay.setRawText("Test training day");
        trainingDay.setCreatedAt(LocalDateTime.now());
        TrainingDay savedTrainingDay = trainingDayRepository.save(trainingDay);
        assertThat(savedTrainingDay.getId()).isNotNull();

        // 5. Link training day to program
        com.example.fitnessbot.model.ProgramTrainingDay programTrainingDay = new com.example.fitnessbot.model.ProgramTrainingDay();
        programTrainingDay.setProgram(savedProgram);
        programTrainingDay.setTrainingDay(savedTrainingDay);
        programTrainingDay.setPosition(1);
        com.example.fitnessbot.model.ProgramTrainingDay savedLink = programTrainingDayRepository.save(programTrainingDay);
        assertThat(savedLink.getId()).isNotNull();

        // 6. Verify the relationship was created
        Optional<ProgramTrainingDay> programTrainingDays = programTrainingDayRepository.findById(savedLink.getId());
        assertThat(programTrainingDays).isNotEmpty();
        assertThat(programTrainingDays.get().getTrainingDay().getId()).isEqualTo(savedTrainingDay.getId());
    }
}