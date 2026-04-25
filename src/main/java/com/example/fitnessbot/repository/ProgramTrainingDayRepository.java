package com.example.fitnessbot.repository;

import com.example.fitnessbot.model.ProgramTrainingDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramTrainingDayRepository extends JpaRepository<ProgramTrainingDay, Long> {
    @Query("""
            select programTrainingDay
            from ProgramTrainingDay programTrainingDay
            join fetch programTrainingDay.trainingDay
            where programTrainingDay.program.id = :programId
            order by programTrainingDay.position asc
            """)
    List<ProgramTrainingDay> findByProgramIdOrderByPositionAsc(@Param("programId") Long programId);
}
