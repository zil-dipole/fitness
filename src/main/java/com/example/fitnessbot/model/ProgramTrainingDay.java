package com.example.fitnessbot.model;

import jakarta.persistence.*;

@Entity
@Table(name = "program_training_days")
public class ProgramTrainingDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_day_id", nullable = false)
    private TrainingDay trainingDay;

    private Integer position;

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public TrainingDay getTrainingDay() {
        return trainingDay;
    }

    public void setTrainingDay(TrainingDay trainingDay) {
        this.trainingDay = trainingDay;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }
}