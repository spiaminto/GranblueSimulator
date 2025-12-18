package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.metadata.domain.omen.Omen;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmenRepository extends JpaRepository<Omen, Long> {
}
