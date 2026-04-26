package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.TrainingDay;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramCreationSessionManagerTest {

    @Test
    void trainingDaysPreserveForwardOrderInsteadOfIdOrder() {
        Program program = new Program();
        ProgramCreationSessionManager.ProgramCreationSession session =
                new ProgramCreationSessionManager.ProgramCreationSession(program);

        session.addTrainingDay(trainingDay(30L, "Треня 1"));
        session.addTrainingDay(trainingDay(10L, "Треня 2"));
        session.addTrainingDay(trainingDay(20L, "Треня 3"));

        assertThat(session.getTrainingDays())
                .extracting(TrainingDay::getTitle)
                .containsExactly("Треня 1", "Треня 2", "Треня 3");
    }

    @Test
    void duplicateTrainingDayReplacesExistingDayWithoutChangingOrder() {
        Program program = new Program();
        ProgramCreationSessionManager.ProgramCreationSession session =
                new ProgramCreationSessionManager.ProgramCreationSession(program);

        session.addTrainingDay(trainingDay(1L, "Треня 1"));
        session.addTrainingDay(trainingDay(2L, "Wrong title"));
        session.addTrainingDay(trainingDay(2L, "Треня 2"));

        List<TrainingDay> trainingDays = session.getTrainingDays();
        assertThat(trainingDays).extracting(TrainingDay::getTitle).containsExactly("Треня 1", "Треня 2");
        assertThat(session.getTrainingDaysCount()).isEqualTo(2);
    }

    private TrainingDay trainingDay(Long id, String title) {
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(id);
        trainingDay.setTitle(title);
        return trainingDay;
    }
}
