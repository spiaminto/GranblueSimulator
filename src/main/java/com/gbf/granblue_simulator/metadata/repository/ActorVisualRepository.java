package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ActorVisualRepository extends JpaRepository<ActorVisual, Long> {
    List<ActorVisual> findByIdIn(Collection<Long> ids);
}
