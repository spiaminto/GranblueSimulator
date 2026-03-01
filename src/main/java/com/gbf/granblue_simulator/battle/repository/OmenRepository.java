package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Omen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OmenRepository extends JpaRepository<Omen, Integer> {
}
