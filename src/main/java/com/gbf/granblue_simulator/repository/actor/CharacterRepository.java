package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.Character;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterRepository extends JpaRepository<Character, Long> {
    List<Actor> findByMemberIdOrderByCurrentOrderAsc(Long memberId);
}
