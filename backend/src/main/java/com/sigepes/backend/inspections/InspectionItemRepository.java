package com.sigepes.backend.inspections;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionItemRepository extends JpaRepository<InspectionParts.ItemEntity, String> {

    List<InspectionParts.ItemEntity> findByInspectionIdOrderByPositionAsc(String inspectionId);
}
