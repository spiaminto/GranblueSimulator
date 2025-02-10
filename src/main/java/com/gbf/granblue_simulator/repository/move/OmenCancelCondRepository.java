package com.gbf.granblue_simulator.repository.move;

import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelCond;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OmenCancelCondRepository extends JpaRepository<OmenCancelCond, Long> {
}
