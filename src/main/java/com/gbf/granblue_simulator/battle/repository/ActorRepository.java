package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActorRepository extends JpaRepository<Actor, Long> {
    
    //CHECK 나중에 Actor fetchjoin 할때 카테시안 곱 문제 생각할것

}
