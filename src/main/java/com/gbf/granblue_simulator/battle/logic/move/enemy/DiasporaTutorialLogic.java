package com.gbf.granblue_simulator.battle.logic.move.enemy;

import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class DiasporaTutorialLogic extends DefaultEnemyMoveLogic{
    private final String gid = "4300903t";

    protected DiasporaTutorialLogic(EnemyMoveLogicDependencies enemyMoveLogicDependencies) {
        super(enemyMoveLogicDependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(chargeAttackKey(gid, "a"), this::chargeAttackA);
        moveLogicRegistry.register(chargeAttackKey(gid, "b"), this::chargeAttackB);
        moveLogicRegistry.register("stb_" + gid, this::triggerOmen);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move attack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(attack));
    }

    // 전조 발생 [TURN_END_OMEN]
    public MoveLogicResult triggerOmen(MoveLogicRequest request) {
        Enemy self = (Enemy) battleContext.getEnemy();

        // 5의 배수 턴 마다 자괴인자 발동
        if ((battleContext.getCurrentTurn() + 1) % 5 == 0) {
            self.updateNextIncantStandbyType(MoveType.STANDBY_B);
        }

        // 전조발생
        MoveLogicResult result = omenLogic.triggerOmen(self).map(standby -> resultMapper.toResult(ResultMapperRequest.from(standby))).orElseGet(resultMapper::emptyResult);

        return result;
    }

    // 경성방사 : 적에게 랜덤 대상 5배 데미지 5회
    protected MoveLogicResult chargeAttackA(MoveLogicRequest request) {
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // 자괴인자: 적 전체에 10배 데미지, 공격력 감소, 방어력 감소 효과 (약화효과 99개 부여)
    protected MoveLogicResult chargeAttackB(MoveLogicRequest request) {
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // [BATTLE_START] 전투 시작시 자신에게 인자발생 부여
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        return resultMapper.fromDefaultResult(defaultAbility(request.getMove()));
    }

}
