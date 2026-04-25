package com.example.fitnessbot.repository;

import com.example.fitnessbot.model.WorkoutSession;
import com.example.fitnessbot.model.WorkoutSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {
    Optional<WorkoutSession> findFirstByUserIdAndStatusOrderByStartedAtDesc(Long userId, WorkoutSessionStatus status);
}
