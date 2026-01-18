package com.example.fitnessbot.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exercises")
public class Exercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_day_id", nullable = false)
    private TrainingDay trainingDay;

    /** Order inside the day */
    private Integer position;

    private String section; // e.g., "Активация разминка"
    private String name;    // exercise name
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

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSets() {
        return sets;
    }

    public void setSets(Integer sets) {
        this.sets = sets;
    }

    public String getRepsOrDuration() {
        return repsOrDuration;
    }

    public void setRepsOrDuration(String repsOrDuration) {
        this.repsOrDuration = repsOrDuration;
    }

    public List<String> getVideoUrls() {
        return videoUrls;
    }

    public void setVideoUrls(List<String> videoUrls) {
        this.videoUrls = videoUrls;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Double getLastWeightKg() {
        return lastWeightKg;
    }

    public void setLastWeightKg(Double lastWeightKg) {
        this.lastWeightKg = lastWeightKg;
    }
}
