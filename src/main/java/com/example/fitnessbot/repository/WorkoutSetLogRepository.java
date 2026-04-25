package com.example.fitnessbot.repository;

import com.example.fitnessbot.model.WorkoutSetLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkoutSetLogRepository extends JpaRepository<WorkoutSetLog, Long> {
    List<WorkoutSetLog> findByUserIdAndExerciseIdAndWorkoutSessionIdNotOrderByCreatedAtDesc(
            Long userId,
            Long exerciseId,
            Long workoutSessionId,
            Pageable pageable
    );
}
