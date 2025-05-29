package com.gbf.granblue_simulator.logic.actor;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.logic.common.ChargeGaugeLogic;
import com.gbf.granblue_simulator.logic.common.DamageLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.StatusUtil;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class ActorLogicUtil {

    private final SetStatusLogic setStatusLogic;
    private final StatusUtil statusUtil;
    private final DamageLogic damageLogic;
    private final ChargeGaugeLogic chargeGaugeLogic;

    /**
     * 아군의 공격무브를 결정후 반환 (오의, 1타, 2타, 3타)
     * @param mainActor
     * @return
     */
    public Move determineAttackMove(BattleActor mainActor) {
        Map<MoveType, Move> moveMap = mainActor.getActor().getMoves();
        // 오의
        if (mainActor.getChargeGauge() >= mainActor.getActor().getMaxChargeGauge())
            return moveMap.get(MoveType.CHARGE_ATTACK_DEFAULT);
        // 평타 횟수 (독립시행)
        MoveType moveType = 
                Math.random() < mainActor.getTripleAttackRate() ? MoveType.TRIPLE_ATTACK :
                Math.random() < mainActor.getDoubleAttackRate() ? MoveType.DOUBLE_ATTACK : MoveType.SINGLE_ATTACK;
        return moveMap.get(moveType);
    }

    /**
     * 기본적인 공격처리
     * 공격 행동 결정(평타횟수) -> 데미지 계산 -> 오의게이지 갱신
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @return DefaultActorLogicResult
     */
    public DefaultActorLogicResult defaultAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 평타 횟수 (독립시행)
        Move attackMove = mainActor.getActor().getMoves().get(
                Math.random() < mainActor.getTripleAttackRate() ? MoveType.TRIPLE_ATTACK :
                Math.random() < mainActor.getDoubleAttackRate() ? MoveType.DOUBLE_ATTACK : MoveType.SINGLE_ATTACK
        );
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, attackMove);
        // 오의게이지
        chargeGaugeLogic.afterAttack(mainActor, partyMembers, attackMove.getType());
        return DefaultActorLogicResult.builder().move(attackMove).damageLogicResult(damageLogicResult).build();
    }

    /**
     * 기본적인 오의 처리
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 오의게이지 갱신
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param changedDamageRate : 변경할 오의 배율 (기본배율 사용시 null)
     * @return DefaultActorLogicResult
     */
    public DefaultActorLogicResult defaultChargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Double changedDamageRate) {
        Move chargeAttack = mainActor.getActor().getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT);
        // 오의 배율 변경확인
        double damageRate = changedDamageRate != null ? changedDamageRate : chargeAttack.getDamageRate();
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, chargeAttack.getType(), chargeAttack.getElementType(), damageRate, chargeAttack.getHitCount());
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, chargeAttack.getStatuses());
        // 오의게이지
        chargeGaugeLogic.afterAttack(mainActor, partyMembers, chargeAttack.getType());
        return DefaultActorLogicResult.builder().move(chargeAttack).damageLogicResult(damageLogicResult).build();
    }


    /**
     * 아군과 적 을 모두 받아 BattleStatus 의 남은시간과 어빌리티 쿨타임을 진행
     */
    @Transactional
    public void progressTurn(BattleActor enemy, List<BattleActor> partyMembers) {
        setStatusLogic.progressBattleStatus(enemy, partyMembers);
        partyMembers.forEach(BattleActor::progressAbilityCoolDown);
    }

    /**
     * 보스의 공격 타겟 결정후 반환 (전체공격의 경우 partyMembers 그대로 사용하면 됨)
     * 적용효과 : 감싸기
     *
     * @param hitCount
     * @param partyMembers
     * @return
     */
    public List<BattleActor> getEnemyAttackTargets(boolean isAllTarget, int hitCount, List<BattleActor> partyMembers) {
        // 감싸기 효과 적용 확인
        Optional<BattleStatus> substituteEffect = statusUtil.getEffectiveCoveringEffect(partyMembers, StatusEffectType.SUBSTITUTE);
        return substituteEffect
                .map(battleStatus -> isAllTarget ?
                        Collections.nCopies(partyMembers.size(), battleStatus.getBattleActor()) : // 전체타겟인 경우 전원분 감싸기 id
                        Collections.nCopies(hitCount, battleStatus.getBattleActor())) // 전체타겟 아닌경우 히트수만큼 감싸기 id
                .orElseGet(() -> isAllTarget ?
                        partyMembers :
                        IntStream.range(0, hitCount)
                                .mapToObj(i -> partyMembers.get((int) (Math.random() * partyMembers.size())))
                                .toList());
    }



}

