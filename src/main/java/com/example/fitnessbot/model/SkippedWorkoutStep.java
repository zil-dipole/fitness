package com.example.fitnessbot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class SkippedWorkoutStep {

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column(name = "replay_whole_exercise", nullable = false)
    private Boolean replayWholeExercise = Boolean.FALSE;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Integer getSetNumber() {
        return setNumber;
    }

    public void setSetNumber(Integer setNumber) {
        this.setNumber = setNumber;
    }

    public Boolean getReplayWholeExercise() {
        return replayWholeExercise;
    }

    public void setReplayWholeExercise(Boolean replayWholeExercise) {
        this.replayWholeExercise = replayWholeExercise;
    }
}
