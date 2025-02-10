package com.gbf.granblue_simulator.repository;

import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BattleStatusRepository extends JpaRepository<BattleStatus, Long> {
}
