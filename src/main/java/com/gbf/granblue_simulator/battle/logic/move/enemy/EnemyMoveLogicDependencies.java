package com.gbf.granblue_simulator.battle.logic.move.enemy;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.logic.move.mapper.EnemyLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicCheckCondition;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRegistry;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.battle.logic.system.OmenLogic;
import com.gbf.granblue_simulator.battle.service.BattleLogService;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.service.BaseActorService;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Getter
public class EnemyMoveLogicDependencies {
    private final BattleContext battleContext;

    private final DamageLogic damageLogic;
    private final SetStatusLogic setStatusLogic;
    private final ChargeGaugeLogic chargeGaugeLogic;
    private final OmenLogic omenLogic;

    private final MoveLogicRegistry moveLogicRegistry;
    private final MoveLogicCheckCondition checkCondition;

    private final MoveService moveService;
    private final BaseMoveService baseMoveService;
    private final BattleLogService battleLogService;
    private final BaseActorService baseActorService;

    private final EnemyLogicResultMapper resultMapper;
}
