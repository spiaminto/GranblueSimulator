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

    private final List<BattleActor> enemyAttackTargets;
    private final Omen resultOmen; // 브레이크 할경우 Move 가 break 가 되서 이전 전조정보를 가져올수 없음

    private final boolean executeChargeAttack; // 다음 행동으로 오의 재발동 시 true (오의 재발동 효과 가진채로 오의발동)
    private final StatusTargetType executeAttackTargetType; // 턴 진행없이 일반공격 할 대상
}
