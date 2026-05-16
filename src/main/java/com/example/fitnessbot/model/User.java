package com.example.fitnessbot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Represents a user of the fitness bot.
 * Users are identified by their Telegram ID and can have multiple training programs.
 */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(name = "telegram_username", length = 32)
    private String telegramUsername;

    private String name;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "use_ai_parser", nullable = false)
    private boolean useAiParser = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_program_id")
    private Program activeProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_training_day_id")
    private TrainingDay activeTrainingDay;

    @Column(name = "active_program_week", nullable = false)
    private Integer activeProgramWeek = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    private UserLanguage language = UserLanguage.ENGLISH;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gets the Telegram ID of the user.
     * @return the Telegram ID
     */
    public Long getTelegramId() {
        return telegramId;
    }

    /**
     * Sets the Telegram ID of the user.
     * @param telegramId the Telegram ID to set
     */
    public void setTelegramId(Long telegramId) {
        this.telegramId = telegramId;
    }

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public void setTelegramUsername(String telegramUsername) {
        if (telegramUsername == null || telegramUsername.isBlank()) {
            this.telegramUsername = null;
            return;
        }

        String normalized = telegramUsername.trim();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        this.telegramUsername = normalized.toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the user's weight in kilograms.
     * @return the weight in kg, or null if not set
     */
    public Double getWeightKg() {
        return weightKg;
    }

    /**
     * Sets the user's weight in kilograms.
     * @param weightKg the weight to set
     */
    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    /**
     * Gets the timestamp when the user was created.
     * @return the creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the timestamp when the user was created.
     * @param createdAt the creation timestamp to set
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isUseAiParser() {
        return useAiParser;
    }

    public void setUseAiParser(boolean useAiParser) {
        this.useAiParser = useAiParser;
    }

    public Program getActiveProgram() {
        return activeProgram;
    }

    public void setActiveProgram(Program activeProgram) {
        this.activeProgram = activeProgram;
    }

    public TrainingDay getActiveTrainingDay() {
        return activeTrainingDay;
    }

    public void setActiveTrainingDay(TrainingDay activeTrainingDay) {
        this.activeTrainingDay = activeTrainingDay;
    }

    public Integer getActiveProgramWeek() {
        return activeProgramWeek;
    }

    public void setActiveProgramWeek(Integer activeProgramWeek) {
        this.activeProgramWeek = activeProgramWeek;
    }

    public UserLanguage getLanguage() {
        return language == null ? UserLanguage.ENGLISH : language;
    }

    public void setLanguage(UserLanguage language) {
        this.language = language == null ? UserLanguage.ENGLISH : language;
    }
}
