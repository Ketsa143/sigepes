package com.sigepes.backend.environment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvZoneRepository extends JpaRepository<EnvZoneEntity, String> {

    List<EnvZoneEntity> findByActiveTrueOrderByNameAsc();

    Optional<EnvZoneEntity> findByNameIgnoreCase(String name);
}
