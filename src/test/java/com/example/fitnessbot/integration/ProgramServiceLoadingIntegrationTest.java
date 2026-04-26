package com.example.fitnessbot.integration;

import com.example.fitnessbot.FitnessBotApplication;
import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.ProgramTrainingDay;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.ExerciseRepository;
import com.example.fitnessbot.repository.ProgramRepository;
import com.example.fitnessbot.repository.ProgramTrainingDayRepository;
import com.example.fitnessbot.repository.TrainingDayRepository;
import com.example.fitnessbot.repository.UserRepository;
import com.example.fitnessbot.service.ProgramService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = FitnessBotApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:program-service-loading;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
class ProgramServiceLoadingIntegrationTest {

    @Autowired
    private ProgramService programService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private TrainingDayRepository trainingDayRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ProgramTrainingDayRepository programTrainingDayRepository;

    @Test
    void getActiveTrainingDayForUserReturnsTrainingDayWithExercisesAndVideoUrlsLoaded() {
        Fixture fixture = createProgramFixture(100001L);

        TrainingDay activeTrainingDay = programService.getActiveTrainingDayForUser(fixture.user().getTelegramId());

        assertThat(activeTrainingDay).isNotNull();
        assertThat(activeTrainingDay.getTitle()).isEqualTo("Day 1");
        assertThat(activeTrainingDay.getExercises()).hasSize(1);
        assertThat(activeTrainingDay.getExercises().getFirst().getName()).isEqualTo("Bench Press");
        assertThat(activeTrainingDay.getExercises().getFirst().getVideoUrls())
                .containsExactly("https://video.example/bench");
    }

    @Test
    void advanceActiveTrainingDayForUserReturnsNextTrainingDayWithExercisesAndVideoUrlsLoaded() {
        Fixture fixture = createProgramFixture(100002L);

        ProgramService.ActiveTrainingDayProgression nextTrainingDay = programService.advanceActiveTrainingDayForUser(fixture.user().getTelegramId());

        assertThat(nextTrainingDay).isNotNull();
        assertThat(nextTrainingDay.weekNumber()).isEqualTo(1);
        assertThat(nextTrainingDay.trainingDay().getTitle()).isEqualTo("Day 2");
        assertThat(nextTrainingDay.trainingDay().getExercises()).hasSize(1);
        assertThat(nextTrainingDay.trainingDay().getExercises().getFirst().getName()).isEqualTo("Squat");
        assertThat(nextTrainingDay.trainingDay().getExercises().getFirst().getVideoUrls())
                .containsExactly("https://video.example/squat");
    }

    private Fixture createProgramFixture(Long telegramUserId) {
        User user = new User();
        user.setTelegramId(telegramUserId);
        user = userRepository.save(user);

        Program program = new Program();
        program.setUser(user);
        program.setName("Strength");
        program = programRepository.save(program);

        TrainingDay firstDay = new TrainingDay();
        firstDay.setUser(user);
        firstDay.setTitle("Day 1");
        firstDay.setRawText("Day 1 raw");
        firstDay = trainingDayRepository.save(firstDay);

        TrainingDay secondDay = new TrainingDay();
        secondDay.setUser(user);
        secondDay.setTitle("Day 2");
        secondDay.setRawText("Day 2 raw");
        secondDay = trainingDayRepository.save(secondDay);

        Exercise firstExercise = new Exercise();
        firstExercise.setTrainingDay(firstDay);
        firstExercise.setName("Bench Press");
        firstExercise.setPosition(1);
        firstExercise.setSets(3);
        firstExercise.setRepsOrDuration("8");
        firstExercise.setVideoUrls(List.of("https://video.example/bench"));
        exerciseRepository.save(firstExercise);

        Exercise secondExercise = new Exercise();
        secondExercise.setTrainingDay(secondDay);
        secondExercise.setName("Squat");
        secondExercise.setPosition(1);
        secondExercise.setSets(3);
        secondExercise.setRepsOrDuration("5");
        secondExercise.setVideoUrls(List.of("https://video.example/squat"));
        exerciseRepository.save(secondExercise);

        ProgramTrainingDay firstLink = new ProgramTrainingDay();
        firstLink.setProgram(program);
        firstLink.setTrainingDay(firstDay);
        firstLink.setPosition(1);
        programTrainingDayRepository.save(firstLink);

        ProgramTrainingDay secondLink = new ProgramTrainingDay();
        secondLink.setProgram(program);
        secondLink.setTrainingDay(secondDay);
        secondLink.setPosition(2);
        programTrainingDayRepository.save(secondLink);

        user.setActiveProgram(program);
        user.setActiveTrainingDay(firstDay);
        userRepository.save(user);

        return new Fixture(user, program, firstDay, secondDay);
    }

    private record Fixture(User user, Program program, TrainingDay firstDay, TrainingDay secondDay) {
    }
}
