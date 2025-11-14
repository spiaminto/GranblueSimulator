package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.omen.Omen;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusEffectTargetType;
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

    private final List<Actor> enemyAttackTargets;
    private final Omen resultOmen; // 브레이크 할경우 Move 가 break 가 되서 이전 전조정보를 가져올수 없음

    private final boolean executeChargeAttack; // 다음 행동으로 오의 재발동 시 true (오의 재발동 효과 가진채로 오의발동)
    private final StatusEffectTargetType executeAttackTargetType; // 턴 진행없이 일반공격 할 대상
}
