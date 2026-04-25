package com.example.fitnessbot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "workout_sessions")
public class WorkoutSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_day_id", nullable = false)
    private TrainingDay trainingDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_exercise_id")
    private Exercise currentExercise;

    @Column(name = "current_set_number", nullable = false)
    private Integer currentSetNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkoutSessionStatus status = WorkoutSessionStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TrainingDay getTrainingDay() {
        return trainingDay;
    }

    public void setTrainingDay(TrainingDay trainingDay) {
        this.trainingDay = trainingDay;
    }

    public Exercise getCurrentExercise() {
        return currentExercise;
    }

    public void setCurrentExercise(Exercise currentExercise) {
        this.currentExercise = currentExercise;
    }

    public Integer getCurrentSetNumber() {
        return currentSetNumber;
    }

    public void setCurrentSetNumber(Integer currentSetNumber) {
        this.currentSetNumber = currentSetNumber;
    }

    public WorkoutSessionStatus getStatus() {
        return status;
    }

    public void setStatus(WorkoutSessionStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
