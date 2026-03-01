package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EffectVisualRepository extends JpaRepository<EffectVisual, Long> {
    List<EffectVisual> findByActorVisual(ActorVisual actorVisual);
}
