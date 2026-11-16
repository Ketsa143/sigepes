package com.sigepes.backend.inspections;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<InspectionEntity, String> {

    /** AC-22.4 · D5: el reenvío desde la cola sin señal devuelve el acta ya guardada. */
    Optional<InspectionEntity> findByClientId(String clientId);

    Optional<InspectionEntity> findByCodeIgnoreCase(String code);

    List<InspectionEntity> findTop200ByOrderByInspectedAtDesc();

    List<InspectionEntity> findByOwnerIdOrderByInspectedAtDesc(String ownerId);

    List<InspectionEntity> findByVesselIdOrderByInspectedAtDesc(String vesselId);
}
