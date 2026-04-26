package com.example.fitnessbot.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an exercise within a training day.
 * Exercises have a position, section, name, sets, reps/duration, videos, and notes.
 */
@Entity
@Table(name = "exercises")
public class Exercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_day_id", nullable = false)
    private TrainingDay trainingDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_exercise_id")
    private Exercise canonicalExercise;

    /** Order inside the day */
    private Integer position;

    private String section; // e.g., "Активация разминка"
    private String name;    // exercise name

    @Column(name = "normalized_name")
    private String normalizedName;

    private Integer sets;   // optional

    @Column(name = "reps_or_duration")
    private String repsOrDuration; // e.g., "10", "20 sec", "x 8"

    @ElementCollection
    @CollectionTable(name = "exercise_videos", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "video_url")
    private List<String> videoUrls = new ArrayList<>();

    @Column(name = "notes")
    private String notes;

    /** Last weight the user used for this exercise */
    @Column(name = "last_weight_kg")
    private Double lastWeightKg;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gets the training day this exercise belongs to.
     * @return the training day
     */
    public TrainingDay getTrainingDay() {
        return trainingDay;
    }

    /**
     * Sets the training day this exercise belongs to.
     * @param trainingDay the training day to set
     */
    public void setTrainingDay(TrainingDay trainingDay) {
        this.trainingDay = trainingDay;
    }

    public Exercise getCanonicalExercise() {
        return canonicalExercise;
    }

    public void setCanonicalExercise(Exercise canonicalExercise) {
        this.canonicalExercise = canonicalExercise;
    }

    /**
     * Gets the position of this exercise within the training day.
     * @return the position
     */
    public Integer getPosition() {
        return position;
    }

    /**
     * Sets the position of this exercise within the training day.
     * @param position the position to set
     */
    public void setPosition(Integer position) {
        this.position = position;
    }

    /**
     * Gets the section this exercise belongs to.
     * @return the section
     */
    public String getSection() {
        return section;
    }

    /**
     * Sets the section this exercise belongs to.
     * @param section the section to set
     */
    public void setSection(String section) {
        this.section = section;
    }

    /**
     * Gets the name of this exercise.
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this exercise.
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    /**
     * Gets the number of sets for this exercise.
     * @return the number of sets, or null if not specified
     */
    public Integer getSets() {
        return sets;
    }

    /**
     * Sets the number of sets for this exercise.
     * @param sets the number of sets to set
     */
    public void setSets(Integer sets) {
        this.sets = sets;
    }

    /**
     * Gets the reps or duration for this exercise.
     * @return the reps or duration
     */
    public String getRepsOrDuration() {
        return repsOrDuration;
    }

    /**
     * Sets the reps or duration for this exercise.
     * @param repsOrDuration the reps or duration to set
     */
    public void setRepsOrDuration(String repsOrDuration) {
        this.repsOrDuration = repsOrDuration;
    }

    /**
     * Gets the list of video URLs for this exercise.
     * @return the list of video URLs
     */
    public List<String> getVideoUrls() {
        return videoUrls;
    }

    /**
     * Sets the list of video URLs for this exercise.
     * @param videoUrls the list of video URLs to set
     */
    public void setVideoUrls(List<String> videoUrls) {
        this.videoUrls = videoUrls;
    }

    /**
     * Gets the notes for this exercise.
     * @return the notes
     */
    public String getNotes() {
        return notes;
    }

    /**
     * Sets the notes for this exercise.
     * @param notes the notes to set
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Gets the last weight used for this exercise.
     * @return the last weight in kg, or null if not set
     */
    public Double getLastWeightKg() {
        return lastWeightKg;
    }

    /**
     * Sets the last weight used for this exercise.
     * @param lastWeightKg the last weight to set
     */
    public void setLastWeightKg(Double lastWeightKg) {
        this.lastWeightKg = lastWeightKg;
    }
}
