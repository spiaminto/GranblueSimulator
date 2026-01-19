package com.gbf.granblue_simulator.battle.logic.actor.dto;

import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DefaultActorLogicResult {
    private final BaseMove resultMove;
    private final DamageLogicResult damageLogicResult;
    private final SetStatusEffectResult setStatusEffectResult;

    private final List<Actor> enemyAttackTargets;
    private final OmenResult resultOmen; // 브레이크 할경우 Move 가 break 가 되서 이전 전조정보를 가져올수 없음

    private final boolean executeChargeAttack; // 다음 행동으로 오의 재발동 시 true (오의 재발동 효과 가진채로 오의발동)
    private final StatusEffectTargetType executeAttackTargetType; // 턴 진행없이 일반공격 할 대상
}
