package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.actor.Actor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActorRepository extends JpaRepository<Actor, Long> {
}
