package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BattleActorRepository extends JpaRepository<BattleActor, Long> {

    List<BattleActor> findByMemberIdOrderByCurrentOrderAsc(Long memberId);
}
