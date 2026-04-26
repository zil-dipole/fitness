package com.example.fitnessbot.repository;

import com.example.fitnessbot.model.TrainingDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainingDayRepository extends JpaRepository<TrainingDay, Long> {
    @Query("""
            select distinct trainingDay
            from TrainingDay trainingDay
            left join fetch trainingDay.exercises exercise
            where trainingDay.id = :id
            """)
    Optional<TrainingDay> findByIdWithExercises(@Param("id") Long id);
}
