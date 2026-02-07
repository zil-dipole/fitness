package com.example.fitnessbot.repository;

import com.example.fitnessbot.model.ProgramTrainingDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramTrainingDayRepository extends JpaRepository<ProgramTrainingDay, Long> {
}