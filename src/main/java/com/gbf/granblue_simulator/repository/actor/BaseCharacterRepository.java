package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.base.actor.BaseCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseCharacterRepository extends JpaRepository<BaseCharacter, Long> {
}
