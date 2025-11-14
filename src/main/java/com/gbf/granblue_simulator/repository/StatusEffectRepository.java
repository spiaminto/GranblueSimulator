package com.gbf.granblue_simulator.repository;

import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StatusEffectRepository extends JpaRepository<StatusEffect, Long> {
}
