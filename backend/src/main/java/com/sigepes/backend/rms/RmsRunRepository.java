package com.sigepes.backend.rms;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RmsRunRepository extends JpaRepository<RmsRunEntity, String> {

    List<RmsRunEntity> findByStatus(RmsRunEntity.Status status);

    Optional<RmsRunEntity> findTopByOrderByCreatedAtDesc();
}
