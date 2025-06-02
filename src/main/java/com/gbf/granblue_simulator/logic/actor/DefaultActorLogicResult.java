package com.gbf.granblue_simulator.logic.actor;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
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
}
