package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DefaultActorLogicResult {
    private final Move resultMove;
    private final DamageLogicResult damageLogicResult;
    private final SetStatusResult setStatusResult;

    private final StatusTargetType nextMoveTargetType; // 아군 기준 후행동 전체화때 사용
    private final MoveType nextMoveType; // 재공격 등 후행동에서 사용할 타입

    private final List<BattleActor> enemyAttackTargets;
    private final Omen resultOmen; // 브레이크 할경우 Move 가 break 가 되서 이전 전조정보를 가져올수 없음
}
