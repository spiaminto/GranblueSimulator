package com.gbf.granblue_simulator.repository.move;

import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StatusEffectRepository extends JpaRepository<StatusEffect, Long> {
}
