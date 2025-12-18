package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.metadata.domain.omen.OmenCancelCond;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OmenCancelCondRepository extends JpaRepository<OmenCancelCond, Long> {
}
