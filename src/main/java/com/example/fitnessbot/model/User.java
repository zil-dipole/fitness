package com.example.fitnessbot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

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

    private String name;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

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
}
