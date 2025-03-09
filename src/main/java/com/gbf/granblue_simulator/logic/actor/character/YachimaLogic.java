package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.actor.ActorLogicUtil;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.NextMoveRequest;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Transactional
@RequiredArgsConstructor
@Slf4j
public class YachimaLogic implements CharacterLogic {
    private final DamageLogic damageLogic;
    private final SetStatusLogic setStatusLogic;
    private final StatusUtil statusUtil;
    private final ChargeGaugeLogic chargeGaugeLogic;
    private final CharacterLogicResultMapper resultMapper;
    private final ActorLogicUtil actorLogicUtil;


    @Override
    public ActorLogicResult attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 데미지
        Move attackMove = actorLogicUtil.determineAttackMove(mainActor);
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, attackMove);
        // 오의게이지
        chargeGaugeLogic.afterAttack(mainActor, partyMembers, attackMove.getType());

        // 공격행동 발생시 서포어비 1 발동
        return resultMapper.toResultWithNextMove(mainActor, enemy, partyMembers, attackMove, damageLogicResult, null,
                NextMoveRequest.of(true, MoveType.FIRST_SUPPORT_ABILITY, StatusTargetType.SELF));
    }

    @Override // 적에게 데미지, 자신에게 추격, 적에게 디그레이드스피넬
    public ActorLogicResult firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move firstAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_ABILITY);
        // 데미지 
        int alphaLevel = statusUtil.getUniqueStatusLevel(mainActor, "알파");
        int hitCount = firstAbility.getHitCount() + alphaLevel;
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, firstAbility, firstAbility.getDamageRate(), hitCount);
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, firstAbility.getStatuses());
        // 쿨타임 적용
        mainActor.setFirstAbilityCoolDown(firstAbility.getCoolDown());

        return resultMapper.toResult(mainActor, enemy, partyMembers, firstAbility, damageLogicResult, setStatusResult);
    }


    @Override // 아군 전체 방어, 뎀컷, 디스펠가드
    public ActorLogicResult secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move secondAbility = mainActor.getActor().getMoves().get(MoveType.SECOND_ABILITY);
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, secondAbility.getStatuses());
        // 쿨타임
        mainActor.setSecondAbilityCoolDown(secondAbility.getCoolDown());

        return resultMapper.toResult(mainActor, enemy, partyMembers, secondAbility, null, setStatusResult);
    }

    @Override // 자신 요다메상승, 통상공격실행 (레코데이션 효과중 전체화)
    public ActorLogicResult thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move thirdAbility = mainActor.getActor().getMoves().get(MoveType.THIRD_ABILITY);
        // 스테이터스 적용
        boolean hasUniqueStatus = statusUtil.hasUniqueStatus(mainActor, "레코데이션 싱크");
        SetStatusResult setStatusResult = null;
        if (hasUniqueStatus) {
            // 레코데이션 효과중 전체화
            setStatusResult = setStatusLogic.setStatusToManualTargets(partyMembers, enemy, partyMembers, thirdAbility);
        } else {
            setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, thirdAbility.getStatuses());
        }
        // 쿨타임
        mainActor.setThirdAbilityCoolDown(thirdAbility.getCoolDown());

        StatusTargetType afterMoveTarget = hasUniqueStatus ? StatusTargetType.PARTY_MEMBERS : StatusTargetType.SELF;
        return resultMapper.toResultWithNextMove(mainActor, enemy, partyMembers, thirdAbility, null, setStatusResult,
                NextMoveRequest.of(true, MoveType.ATTACK, afterMoveTarget));
    }

    @Override // 데미지, 1어빌발동, 레코데이션 싱크시 오의배율 극대로 변화
    public ActorLogicResult chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move chargeAttack = mainActor.getActor().getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT);
        // 데미지
        DamageLogicResult damageLogicResult = null;
        if (statusUtil.hasUniqueStatus(mainActor, "레코데이션 싱크")) {
            // 레코데이션 싱크중 배율 극대
            damageLogicResult = damageLogic.process(mainActor, enemy, chargeAttack);
        } else {
            damageLogicResult = damageLogic.process(mainActor, enemy, chargeAttack);
        }
        // 오의게이지
        chargeGaugeLogic.afterAttack(mainActor, partyMembers, chargeAttack.getType());

        // 1어빌 자동발동
        return resultMapper.toResultWithNextMove(mainActor, enemy, partyMembers, chargeAttack, damageLogicResult, null,
                NextMoveRequest.of(true, MoveType.FIRST_ABILITY, StatusTargetType.SELF));
    }

    @Override // 자신이 공격행동시 사포아비1 적용 (알파레벨 증가)
    public ActorLogicResult firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
        return statusUtil.getBattleStatusByName(mainActor, "알파")
                .filter(alphaBattleStatus -> !alphaBattleStatus.isMaxLevel()) // 만렙 아닌경우만 스테이터스 부여
                .map(alphaBattleStatus -> resultMapper.toResult(mainActor, enemy, partyMembers, firstSupportAbility, null,
                        setStatusLogic.setStatus(mainActor, enemy, partyMembers, firstSupportAbility.getStatuses()))).orElseGet(resultMapper::emptyResult);
    }

    @Override
    public ActorLogicResult secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult thirdSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult postProcessOtherMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult postProcessEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // TODO 적의 무브 결과를 받아와서 해야될듯.
        // 델타레벨 증가확인
        Move secondSupportAbility = mainActor.getActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY);
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, secondSupportAbility.getStatuses());
        return resultMapper.toResult(mainActor, enemy, partyMembers, secondSupportAbility, null, setStatusResult);

    }

    @Override
    public ActorLogicResult onBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        setStatusLogic.initStatus(mainActor);
        return null;
    }

    @Override
    public ActorLogicResult onTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        log.info("mainActor = {}", mainActor);
        boolean isAlphaLevelMax = statusUtil.isUniqueStatusReachedLevel(mainActor, "알파", 4);
        boolean isDeltaLevelMax = statusUtil.isUniqueStatusReachedLevel(mainActor, "델타", 4);
        Optional<BattleStatus> recordationSyncOptional = statusUtil.getBattleStatusByName(mainActor, "레코데이션");
        if (isDeltaLevelMax && isAlphaLevelMax && recordationSyncOptional.isEmpty()) {
            // 레코데이션 싱크 없고, 알파와 델타레벨이 모두 최고레벨일때 자신에게 서폿3 레코데이션 싱크 적용
            Move thirdSupportAbility = mainActor.getActor().getMoves().get(MoveType.THIRD_SUPPORT_ABILITY);
            SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, thirdSupportAbility.getStatuses());

            // 자신을 제외한 아군 전체에게 서폿 1, 2 알파와 델타 레벨 적용
            Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
            Move secondSupportAbility = mainActor.getActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY);
            partyMembers.forEach(partyMember -> log.info("partyMembers = {}, equals mainActor = {}", partyMember, partyMember.equals(mainActor)));
            List<BattleActor> others = new ArrayList<>(partyMembers);
            boolean remove = others.remove(mainActor);
            log.info("removed = {}", remove);
            List<Status> statuses = new ArrayList<>();
            statuses.addAll(firstSupportAbility.getStatuses());
            statuses.addAll(secondSupportAbility.getStatuses());
            setStatusLogic.setStatusToManualTargets(others, enemy, partyMembers, statuses); // 이쪽결과는 이펙트 표시 x

            // 레벨 4로 변경 및 실적용
            log.info("others = {}", others);
            others.forEach(other -> log.info("other = {}", other));
            statusUtil.addUniqueStatusLevelAll(others, 4, "알파", "델타");
            partyMembers.forEach(setStatusLogic::syncStatus);

            // 자신의 3어빌 쿨타임 0으로 감소
            mainActor.setFirstAbilityCoolDown(0);
            return resultMapper.toResult(mainActor, enemy, partyMembers, thirdSupportAbility, null, setStatusResult);
        }
        return null;
    }
}
