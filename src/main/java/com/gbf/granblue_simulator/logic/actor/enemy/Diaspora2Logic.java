package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.Enemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.logic.actor.ActorLogicUtil;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.service.BattleLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class Diaspora2Logic implements EnemyLogic {

    private final StatusUtil statusUtil;
    private final ActorLogicUtil actorLogicUtil;
    private final DamageLogic damageLogic;
    private final EnemyLogicResultMapper enemyLogicResultMapper;
    private final ChargeGaugeLogic chargeGaugeLogic;
    private final SetStatusLogic setStatusLogic;
    private final BattleLogService battleLogService;
    private final OmenLogic omenLogic;

    /**
     * 스테이터스 효과는 한번만 들어가므로 중복제거
     *
     * @param targets
     * @return
     */
    protected List<BattleActor> getStatusTargets(List<BattleActor> targets) {
        return targets.stream().distinct().toList();
    }

    @Override
    public ActorLogicResult attack(BattleActor mainActor, List<BattleActor> partyMembers) {
        Move attackMove = actorLogicUtil.determineAttackMove(mainActor);
        // 타겟
        List<BattleActor> targets = actorLogicUtil.getEnemyAttackTargets(attackMove.isAllTarget(), attackMove.getHitCount(), partyMembers);
        // 데미지
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, attackMove);
        List<Integer> targetOrders = targets.stream().map(BattleActor::getCurrentOrder).toList();
        // 차지턴
        chargeGaugeLogic.afterEnemyAttack(mainActor, targets, damageLogicResult.getDamages(), attackMove.getType(), null);

        return enemyLogicResultMapper.attackToResult(mainActor, partyMembers, attackMove, damageLogicResult, targetOrders);
    }

    @Override
    public ActorLogicResult secondAbility(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult thirdAbility(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult chargeAttack(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        Move standByMove = enemy.getActor().getMoves().get(enemy.getNextStandbyType());
        Omen omen = standByMove.getOmen();
        Move chargeAttack = enemy.getActor().getMoves().get(standByMove.getType().getChargeAttackType());
        // 타겟
        List<BattleActor> targets = actorLogicUtil.getEnemyAttackTargets(chargeAttack.isAllTarget(), chargeAttack.getHitCount(), partyMembers);
        List<BattleActor> statusTargets = getStatusTargets(targets);
        List<Integer> targetOrders = targets.stream().map(BattleActor::getCurrentOrder).toList();
        // 데미지
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, chargeAttack);
        // 스테이터스
        SetStatusResult setStatusResult = setStatusLogic.setRandomStatusFromMove(mainActor, mainActor, statusTargets, chargeAttack);
        // 차지턴 (차지어택의 경우 초기화, 아니면 그대로)
        chargeGaugeLogic.afterEnemyAttack(mainActor, targets, damageLogicResult.getDamages(), chargeAttack.getType(), omen.getOmenType());
        // 스탠바이 초기화
        enemy.setNextStandbyType(null);
        return enemyLogicResultMapper.toResult(mainActor, partyMembers, chargeAttack, damageLogicResult, targetOrders, setStatusResult);
    }


    public ActorLogicResult firstSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }


    public ActorLogicResult secondSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }


    public ActorLogicResult thirdSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult postProcessOtherMove(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult postProcessEnemyMove(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult onBattleStart(BattleActor mainActor, List<BattleActor> partyMembers) {
        setStatusLogic.initStatus(mainActor);

        Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, partyMembers, firstSupportAbility.getStatuses());

        return enemyLogicResultMapper.toResult(mainActor, partyMembers, firstSupportAbility, null, null, setStatusResult);
    }

    @Override
    public List<ActorLogicResult> onOtherMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult otherResult) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        Enemy enemyActor = (Enemy) enemy.getActor();
        List<ActorLogicResult> results = new ArrayList<>();

        // 전조처리
        if (enemy.getNextStandbyType() != null) {
            Move standbyMove = enemy.getActor().getMoves().get(enemy.getNextStandbyType());
            Omen standbyOmen = standbyMove.getOmen();
            Integer processedOmenValue = omenLogic.processOmen(enemy, otherResult);
            if (processedOmenValue == 0) {
                // 전조 해제됨
                Move breakMove = enemy.getActor().getMoves().get(standbyMove.getType().getBreakType());
                enemy.setNextStandbyType(null);
                if (standbyOmen.getOmenType() == OmenType.CHARGE_ATTACK) enemy.setChargeGauge(0);
                results.add(enemyLogicResultMapper.toResultWithOmen(mainActor, partyMembers, breakMove, standbyOmen));
            } else {
                // 전조 갱신됨
                results.add(enemyLogicResultMapper.toResultWithOmen(mainActor, partyMembers, standbyMove, standbyMove.getOmen()));
            }
        }

        return results;
    }

    @Override
    public List<ActorLogicResult> onTurnEnd(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        List<ActorLogicResult> results = new ArrayList<>();
        // 전조발생
        Move standbyMove = omenLogic.determineStandbyMove(enemy);
        if (standbyMove != null) {
            // 전조 발생함
            return List.of(enemyLogicResultMapper.toResultWithOmen(enemy, partyMembers, standbyMove, standbyMove.getOmen()));
        }
        return new ArrayList<>();
    }

}

