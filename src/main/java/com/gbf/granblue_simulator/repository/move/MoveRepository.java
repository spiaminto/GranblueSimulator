package com.gbf.granblue_simulator.repository.move;

import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoveRepository extends JpaRepository<Move, Long> {

    List<Move> findByTypeAndElementType(MoveType moveType, ElementType elementType);

}
