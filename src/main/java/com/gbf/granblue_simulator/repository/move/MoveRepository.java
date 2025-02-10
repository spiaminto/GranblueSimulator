package com.gbf.granblue_simulator.repository.move;

import com.gbf.granblue_simulator.domain.move.Move;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoveRepository extends JpaRepository<Move, Long> {
}
