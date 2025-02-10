package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BattleEnemyRepository extends JpaRepository<BattleEnemy, Long> {
}
