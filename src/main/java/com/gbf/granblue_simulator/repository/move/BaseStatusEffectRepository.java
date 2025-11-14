package com.gbf.granblue_simulator.repository.move;

import com.gbf.granblue_simulator.domain.base.statuseffect.BaseStatusEffect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BaseStatusEffectRepository extends JpaRepository<BaseStatusEffect, Long> {
}
