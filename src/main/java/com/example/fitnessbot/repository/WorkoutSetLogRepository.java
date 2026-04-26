package com.example.fitnessbot.repository;

import com.example.fitnessbot.model.WorkoutSetLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkoutSetLogRepository extends JpaRepository<WorkoutSetLog, Long> {
    List<WorkoutSetLog> findByWorkoutSessionIdAndExerciseIdOrderBySetNumberAsc(Long workoutSessionId, Long exerciseId);

    @Query("""
            select setLog
            from WorkoutSetLog setLog
            join setLog.exercise exercise
            left join exercise.canonicalExercise canonicalExercise
            where setLog.user.id = :userId
              and setLog.workoutSession.id <> :workoutSessionId
              and (
                   exercise.id = :canonicalExerciseId
                   or canonicalExercise.id = :canonicalExerciseId
                   or (:normalizedName is not null and exercise.normalizedName = :normalizedName)
              )
            order by setLog.createdAt desc
            """)
    List<WorkoutSetLog> findHistoryLogsForExerciseIdentity(
            @Param("userId") Long userId,
            @Param("canonicalExerciseId") Long canonicalExerciseId,
            @Param("normalizedName") String normalizedName,
            @Param("workoutSessionId") Long workoutSessionId,
            Pageable pageable
    );
}
