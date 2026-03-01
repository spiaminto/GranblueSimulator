package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseOmenRepository extends JpaRepository<BaseOmen, Long> {
}
