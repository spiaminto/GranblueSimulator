package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BattleCharacterRepository extends JpaRepository<BattleCharacter, Long> {
    List<BattleActor> findByMemberIdOrderByCurrentOrderAsc(Long memberId);
}
