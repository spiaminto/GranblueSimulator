package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.battle.repository.MoveRepository;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class MoveService {

    private final MoveRepository moveRepository;

    public Optional<Move> findById(Long id) {
        return moveRepository.findById(id);
    }

    public List<Move> findAllByIds(List<Long> moveIds) {
        return moveRepository.findAllById(moveIds);
    }

    /**
     * 트리거된 Move 를 저장. 기존에 있는경우 기존의 트리거된 Move 의 조건을 초기화 하고 그대로 사용, 저장안함
     *
     * @param moves triggered move
     */
    public void saveTriggeredMoves(List<Move> moves) {
        List<Move> toSaveMove = new ArrayList<>(moves);
        for (Move move : moves) {
            if (move.getType() != MoveType.TRIGGERED_ABILITY)
                throw new IllegalArgumentException("[saveTriggeredMoves] not triggered move, move = " + move.getBaseMove().getName() + " type = {}" + move.getType());
            Actor actor = move.getActor();
            List<Move> triggeredAbilities = actor.getMoves(MoveType.TRIGGERED_ABILITY);
            for (Move triggeredAbility : triggeredAbilities) {
                if (triggeredAbility.getId() != null
                        && move.getBaseMove().getLogicId().equals(triggeredAbility.getBaseMove().getLogicId())) {
                    // 자신을 제외한 동일한 로직의 triggerAbility 존재 -> 일단 조건 초기화 후 저장 X (꼭 컨디션을 초기화 해야되는지는 의문)
                    TrackingConditionUtil.resetAllConditions(triggeredAbility.getConditionTracker());
                    toSaveMove.remove(move);
                }
            }
        }
        moveRepository.saveAll(toSaveMove);
    }

    public void saveAll(List<Move> moves) {
        moveRepository.saveAll(moves);
    }

    /**
     * Move 엔티티 제거 및 매핑 삭제
     */
    public void delete(Move move) {
        move.getActor().removeMove(move);
        moveRepository.delete(move);
    }

    public void deleteAll(List<Move> moves) {
        if (!moves.isEmpty()) {
            moveRepository.deleteAll(moves);
        }
    }

}
