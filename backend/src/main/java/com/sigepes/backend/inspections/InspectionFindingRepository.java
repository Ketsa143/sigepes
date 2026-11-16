package com.sigepes.backend.inspections;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionFindingRepository extends JpaRepository<InspectionParts.FindingEntity, String> {

    List<InspectionParts.FindingEntity> findByInspectionIdOrderByPositionAsc(String inspectionId);

    List<InspectionParts.FindingEntity> findByInspectionIdIn(Collection<String> inspectionIds);
}
