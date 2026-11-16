package com.sigepes.backend.sanctions;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionProcessRepository extends JpaRepository<SanctionProcessEntity, String> {

    boolean existsByInspectionId(String inspectionId);

    java.util.Optional<SanctionProcessEntity> findByInspectionId(String inspectionId);

    List<SanctionProcessEntity> findAllByOrderByOpenedAtDesc();

    List<SanctionProcessEntity> findByInvestigatedIdOrderByOpenedAtDesc(String investigatedId);

    long countByNumberStartingWith(String prefix);
}
