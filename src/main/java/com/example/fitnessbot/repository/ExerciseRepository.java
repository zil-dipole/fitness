package com.example.fitnessbot.repository;

import com.example.fitnessbot.model.Exercise;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    @Query("""
            select exercise
            from Exercise exercise
            join exercise.trainingDay trainingDay
            where trainingDay.user.id = :userId
              and exercise.normalizedName = :normalizedName
              and exercise.canonicalExercise is null
            order by exercise.id asc
            """)
    List<Exercise> findCanonicalExercisesForUser(
            @Param("userId") Long userId,
            @Param("normalizedName") String normalizedName,
            Pageable pageable
    );
}
