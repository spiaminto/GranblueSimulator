package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.battle.actor.Enemy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnemyRepository extends JpaRepository<Enemy, Long> {
    Optional<Enemy> findByMemberId(long memberId);
}
