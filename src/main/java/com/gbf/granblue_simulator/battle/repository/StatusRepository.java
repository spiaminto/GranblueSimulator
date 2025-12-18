package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StatusRepository extends JpaRepository<Status, Long> {
}
