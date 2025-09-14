package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.NextMoveRequest;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.*;

@Component
@Transactional
@Slf4j
public class YachimaLogic extends CharacterLogic {

    public YachimaLogic(CharacterLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, CalcStatusLogic calcStatusLogic) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic);
    }

    @Override
    public List<ActorLogicResult> processBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return Collections.emptyList();
    }

    @Override
    public ActorLogicResult attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        DefaultActorLogicResult defaultActorLogicResult = super.defaultAttack(mainActor, enemy, partyMembers);
        return resultMapper.attackToResult(mainActor, enemy, partyMembers, defaultActorLogicResult.getResultMove(), defaultActorLogicResult.getDamageLogicResult());
    }

    // 데미지, 1어빌발동, 레코데이션 싱크시 오의배율 극대로 변화
    @Override
    public ActorLogicResult chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 오의 배율 변화
        Double damageRate = hasBattleStatus(mainActor, "레코데이션 싱크") ? 12.5 : null;
        DefaultActorLogicResult chargeAttackResult = super.defaultChargeAttack(mainActor, enemy, partyMembers, damageRate);
        return resultMapper.toResult(mainActor, enemy, partyMembers, chargeAttackResult.getResultMove(), chargeAttackResult.getDamageLogicResult(), chargeAttackResult.getSetStatusResult());
    }

    // 자신이 레코데이션 싱크 효과중 오의 발동 후 1어빌 자동발동
    protected ActorLogicResult chargeAttackAfter(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        if (hasBattleStatus(mainActor, "레코데이션")) {
            return firstAbility(mainActor, enemy, partyMembers, mainActor.getActor().getMoves().get(MoveType.FIRST_ABILITY));
        }
        return resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToPartyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, ActorLogicResult partyMoveResult) {
        if (partyMoveResult.getMainBattleActorId().equals(mainActor.getId())) { // 행동 주체가 자신일때
            if (partyMoveResult.getMoveType().getParentType() == MoveType.ATTACK) { // 일반공격시
                if (hasBattleStatus(mainActor, "레코데이션")) { // 레코데이션 효과중
                    return fourthSupportAbility(mainActor, enemy, partyMembers, mainActor.getActor().getMoves().get(MoveType.FOURTH_SUPPORT_ABILITY));
                } else {// 레코디이션 효과 없을때, 자신이 일반공격시 서포트 어빌리티 1 발동
                    return firstSupportAbility(mainActor, enemy, partyMembers, mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY));
                }
            }
            if (partyMoveResult.getMoveType() == MoveType.CHARGE_ATTACK_DEFAULT) { // 오의사용시 1어비 자동발동
                return chargeAttackAfter(mainActor, enemy, partyMembers, null);
            }
        }
        return resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, ActorLogicResult enemyMoveResult) {
        // 적에게 데미지를 받으면 서포어비 2 발동
        return secondSupportAbility(mainActor, enemy, partyMembers, mainActor.getActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY));
    }

    @Override
    public List<ActorLogicResult> processTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 턴 종료시 서포어비 3 확인
        return List.of(thirdSupportAbility(mainActor, enemy, partyMembers, mainActor.getActor().getMoves().get(MoveType.THIRD_SUPPORT_ABILITY)));
    }

    // 어빌리티

    // 적에게 데미지, 자신에게 추격, 적에게 디그레이드스피넬
    @Override
    protected ActorLogicResult firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move firstAbility) {
        // 알파레벨에 비례해 히트수 증가
        int hitCount = firstAbility.getHitCount() + getUniqueStatusLevel(mainActor, "알파");
        DefaultActorLogicResult defaultActorLogicResult = defaultAbility(mainActor, enemy, partyMembers, firstAbility, null, hitCount);
        return resultMapper.toResult(mainActor, enemy, partyMembers, firstAbility, defaultActorLogicResult.getDamageLogicResult(), defaultActorLogicResult.getSetStatusResult());
    }


    // 아군 전체 방어, 뎀컷, 디스펠가드
    @Override
    protected ActorLogicResult secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move secondAbility) {
        DefaultActorLogicResult defaultActorLogicResult = defaultAbility(mainActor, enemy, partyMembers, secondAbility);
        return resultMapper.toResult(mainActor, enemy, partyMembers, secondAbility, null, defaultActorLogicResult.getSetStatusResult());
    }

    // 자신 요다메상승, 턴 진행 없이 통상공격실행 (레코데이션 효과중 전체화)
    @Override
    protected ActorLogicResult thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move thirdAbility) {
        StatusTargetType afterMoveTarget = StatusTargetType.SELF; // 턴진행 없이 통상공격 실행 타겟
        SetStatusResult setStatusResult = null;
        if (hasBattleStatus(mainActor, "레코데이션 싱크")) {
            // 레코데이션 싱크 효과중 효과 전체화
            setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, thirdAbility.getStatuses(), StatusTargetType.PARTY_MEMBERS);
            afterMoveTarget = StatusTargetType.PARTY_MEMBERS;
        } else {
            // 레코데이션 싱크 x 자신만 적용
            setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, thirdAbility.getStatuses());
        }
        // 쿨타임 적용
        mainActor.updateAbilityCoolDown(thirdAbility.getCoolDown(), MoveType.THIRD_ABILITY);
        return resultMapper.toResultWithExecuteAttack(mainActor, enemy, partyMembers, thirdAbility, null, setStatusResult, afterMoveTarget);
    }

    // 자신이 공격행동시 사포아비1 적용 (알파레벨 증가)
    @Override
    protected ActorLogicResult firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        return isStatusSetSkippable(mainActor, "알파") ?
                resultMapper.emptyResult() :
                resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, setStatusLogic.setStatus(mainActor, enemy, partyMembers, ability.getStatuses()));
    }

    // 자신이 적에게 공격받을시 델타레벨 증가
    @Override
    protected ActorLogicResult secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        return isStatusSetSkippable(mainActor, "델타") ?
                resultMapper.emptyResult() :
                resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, setStatusLogic.setStatus(mainActor, enemy, partyMembers, ability.getStatuses()));
    }

    // 자신이 알파레벨, 델타레벨 최대치인경우 턴 종료시 레코데이션 싱크 효과, 알파 델타를 아군 전체에 적용
    @Override
    protected ActorLogicResult thirdSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        Optional<BattleStatus> recordationSyncOptional = getBattleStatusByName(mainActor, "레코데이션");
        if (recordationSyncOptional.isEmpty()) {
            if (isUniqueStatusReachedLevel(mainActor, "알파", 4) && isUniqueStatusReachedLevel(mainActor, "델타", 4)) {
                // 레코데이션 싱크 적용
                SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, ability.getStatuses());
                // 자신을 포함한 아군 전체에게 알파, 델타 효과 재적용 (타겟 아군 전체로 변경)
                Status statusAlpha = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY).getStatuses().getFirst();
                Status statusDelta = mainActor.getActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY).getStatuses().getFirst();
                setStatusLogic.setStatus(mainActor, enemy, partyMembers, List.of(statusAlpha, statusDelta), StatusTargetType.PARTY_MEMBERS); // 이쪽결과는 이펙트 표시 x

                // 재적용 된 알파, 델타 레벨 4로 변경 및 스탯 갱신
                partyMembers.forEach(partyMember -> setStatusLogic.addBattleStatusesLevel(partyMember, 3, statusAlpha.getId(), statusDelta.getId()));

                // 자신의 3어빌 쿨타임 0으로 감소
                mainActor.updateAbilityCoolDown(0, MoveType.THIRD_ABILITY);
                return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, setStatusResult);
            }
        }
        return resultMapper.emptyResult();
    }

    // 자신이 레코데이션 싱크 효과중 통상공격 후 5배 데미지 3회, 방어력 다운
    @Override
    protected ActorLogicResult fourthSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability, null, null);
        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, defaultResult.getDamageLogicResult(), defaultResult.getSetStatusResult());
    }
}
