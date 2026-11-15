package com.sigepes.backend.geo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarineProtectedAreaRepository extends JpaRepository<MarineProtectedAreaEntity, String> {

    List<MarineProtectedAreaEntity> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
