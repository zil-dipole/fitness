package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.TrainingDay;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramCreationSessionManagerTest {

    @Test
    void awaitingProgramNameIsClearedWhenDraftSessionStarts() {
        ProgramCreationSessionManager manager = TestProgramCreationSessionManagers.redisBacked();
        Program program = new Program();

        manager.startAwaitingProgramName(123L);
        assertThat(manager.isAwaitingProgramName(123L)).isTrue();
        assertThat(manager.hasProgramCreationInProgress(123L)).isTrue();

        manager.startSession(123L, program);

        assertThat(manager.isAwaitingProgramName(123L)).isFalse();
        assertThat(manager.hasActiveSession(123L)).isTrue();
        assertThat(manager.hasProgramCreationInProgress(123L)).isTrue();
    }

    @Test
    void endingSessionAlsoClearsPendingProgramNamePrompt() {
        ProgramCreationSessionManager manager = TestProgramCreationSessionManagers.redisBacked();

        manager.startAwaitingProgramName(123L);
        manager.endSession(123L);

        assertThat(manager.isAwaitingProgramName(123L)).isFalse();
        assertThat(manager.hasProgramCreationInProgress(123L)).isFalse();
    }

    @Test
    void redisBackedSessionPersistsPromptStateAndDraftSnapshot() {
        ProgramCreationSessionManager manager = TestProgramCreationSessionManagers.redisBacked();
        Program program = new Program();
        program.setId(77L);
        program.setName("Redis Program");

        manager.startAwaitingProgramName(123L);
        assertThat(manager.isAwaitingProgramName(123L)).isTrue();

        manager.startSession(123L, program);
        assertThat(manager.isAwaitingProgramName(123L)).isFalse();
        assertThat(manager.hasActiveSession(123L)).isTrue();

        ProgramCreationSessionManager.ProgramCreationSession session = manager.getSession(123L);
        session.addTrainingDay(trainingDay(10L, "Upper Body"));

        ProgramCreationSessionManager.ProgramCreationSession reloaded = manager.getSession(123L);
        assertThat(reloaded.getProgram().getId()).isEqualTo(77L);
        assertThat(reloaded.getProgram().getName()).isEqualTo("Redis Program");
        assertThat(reloaded.getTrainingDays()).extracting(TrainingDay::getTitle).containsExactly("Upper Body");

        reloaded.addTrainingDay(trainingDay(10L, "Updated Upper Body"));
        assertThat(manager.getSession(123L).getTrainingDays())
                .extracting(TrainingDay::getTitle)
                .containsExactly("Updated Upper Body");

        manager.endSession(123L);
        assertThat(manager.hasActiveSession(123L)).isFalse();
        assertThat(manager.getSession(123L)).isNull();
    }

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
