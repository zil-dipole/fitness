package com.example.fitnessbot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a training day containing a collection of exercises.
 * A training day is associated with a user and can be part of multiple programs.
 */
@Entity
@Table(name = "training_days")
public class TrainingDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String title; // e.g. "Треня 3"

    @Column(name = "raw_text")
    private String rawText; // original forwarded message

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "trainingDay", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Exercise> exercises;

    @OneToMany(mappedBy = "trainingDay", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramTrainingDay> programTrainingDays;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gets the user who owns this training day.
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * Sets the user who owns this training day.
     * @param user the user to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Gets the title of the training day.
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the title of the training day.
     * @param title the title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Gets the raw text of the original forwarded message.
     * @return the raw text
     */
    public String getRawText() {
        return rawText;
    }

    /**
     * Sets the raw text of the original forwarded message.
     * @param rawText the raw text to set
     */
    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    /**
     * Gets the timestamp when the training day was created.
     * @return the creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the timestamp when the training day was created.
     * @param createdAt the creation timestamp to set
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the list of exercises in this training day.
     * @return the list of exercises
     */
    public List<Exercise> getExercises() {
        return exercises;
    }

    /**
     * Sets the list of exercises in this training day.
     * @param exercises the list of exercises to set
     */
    public void setExercises(List<Exercise> exercises) {
        this.exercises = exercises;
    }

    /**
     * Gets the list of program-training day associations.
     * @return the list of program-training day associations
     */
    public List<ProgramTrainingDay> getProgramTrainingDays() {
        return programTrainingDays;
    }

    /**
     * Sets the list of program-training day associations.
     * @param programTrainingDays the list of program-training day associations to set
     */
    public void setProgramTrainingDays(List<ProgramTrainingDay> programTrainingDays) {
        this.programTrainingDays = programTrainingDays;
    }
}
