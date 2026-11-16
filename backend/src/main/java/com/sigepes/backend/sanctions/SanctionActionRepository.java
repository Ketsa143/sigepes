package com.sigepes.backend.sanctions;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionActionRepository extends JpaRepository<SanctionActionEntity, String> {

    List<SanctionActionEntity> findByProcessIdOrderBySequenceNoAsc(String processId);
}
