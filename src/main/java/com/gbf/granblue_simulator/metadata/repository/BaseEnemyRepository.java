package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BaseEnemyRepository extends JpaRepository<BaseEnemy, Long> {
    List<BaseEnemy> findByRootNameEn(String rootNameEn);

    List<BaseEnemy> findAllByFormOrder(Integer formOrder);
}
