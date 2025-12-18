package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseCharacterRepository extends JpaRepository<BaseCharacter, Long> {
}
