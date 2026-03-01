package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BaseStatusEffectRepository extends JpaRepository<BaseStatusEffect, Long> {
    List<BaseStatusEffect> findByName(String name);
}
