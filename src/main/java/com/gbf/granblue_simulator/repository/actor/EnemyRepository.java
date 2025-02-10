package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.actor.Enemy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnemyRepository extends JpaRepository<Enemy, Long> {
}
