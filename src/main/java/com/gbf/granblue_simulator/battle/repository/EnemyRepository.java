package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnemyRepository extends JpaRepository<Enemy, Long> {
    Optional<Enemy> findByMemberId(long memberId);
}
