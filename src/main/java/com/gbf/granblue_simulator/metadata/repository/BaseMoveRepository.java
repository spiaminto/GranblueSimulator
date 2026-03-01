package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BaseMoveRepository extends JpaRepository<BaseMove, Long> {

    List<BaseMove> findByTypeAndElementType(MoveType moveType, ElementType elementType);

    List<BaseMove> findAllByLogicIdIn(List<String> logicIds);

    Optional<BaseMove> findByLogicId(String logicId);

}
