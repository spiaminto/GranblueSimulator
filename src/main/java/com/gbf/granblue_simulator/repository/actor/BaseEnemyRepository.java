package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.base.actor.BaseEnemy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseEnemyRepository extends JpaRepository<BaseEnemy, Long> {
}
