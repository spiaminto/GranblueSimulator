package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BattleEnemyRepository extends JpaRepository<BattleEnemy, Long> {
    Optional<BattleEnemy> findByMemberId(long memberId);
}
