package com.sigepes.backend.rms;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RmsQuotaProposalRepository extends JpaRepository<RmsQuotaProposalEntity, String> {

    List<RmsQuotaProposalEntity> findTop100ByOrderByCreatedAtDesc();
}
