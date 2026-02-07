package com.example.fitnessbot.repository;

import com.example.fitnessbot.model.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {
    List<Program> findByUserId(Long userId);
    Optional<Program> findByIdAndUserId(Long id, Long userId);
}