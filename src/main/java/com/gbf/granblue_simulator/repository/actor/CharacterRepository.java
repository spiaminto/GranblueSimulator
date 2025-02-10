package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.actor.Character;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterRepository extends JpaRepository<Character, Long> {
}
