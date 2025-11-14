package com.gbf.granblue_simulator.repository.actor;

import com.gbf.granblue_simulator.domain.base.actor.BaseActor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BaseActorRepository extends JpaRepository<BaseActor, Long> {

    List<BaseActor> findByNameEnContains(String nameEn);

}
