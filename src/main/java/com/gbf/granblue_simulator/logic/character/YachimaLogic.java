package com.gbf.granblue_simulator.logic.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusType;
import com.gbf.granblue_simulator.logic.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional
@RequiredArgsConstructor
@Slf4j
public class YachimaLogic implements CharacterLogic {
    private final Integer baseFirstAbilityHitCount = 6;
    private final DamageLogic damageLogic;
    private final CommonStatusLogic commonStatusLogic;
    private final CommonLogic commonLogic;
    private final CommonInitLogic commonInitLogic;


    @Override
    public void attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        DamageLogicResult damageResult = damageLogic.processAttack(mainActor, enemy);

        Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
        commonStatusLogic.setStatusToActors(mainActor, enemy, partyMembers, firstSupportAbility);
    }

    @Override // 적에게 데미지, 자신에게 추격, 적에게 디그레이드스피넬
    public void firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move firstAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_ABILITY);

        int alphaLevel = commonLogic.getUniqueStatusLevel(mainActor.getBattleStatuses(), "알파");
        int hitCount = baseFirstAbilityHitCount + alphaLevel;
        damageLogic.processAbilityAttack(mainActor, enemy, firstAbility.getDamageRate(), hitCount);

        // 스테이터스 적용
        commonStatusLogic.setStatusToActors(mainActor, enemy, partyMembers, firstAbility);

        // 스테이터스 계산값 적용
        commonLogic.syncStatus(mainActor, commonLogic.getStatusEffectMap(mainActor));
        commonLogic.syncStatus(enemy, commonLogic.getStatusEffectMap(enemy));

        mainActor.setFirstAbilityCoolDown(firstAbility.getCoolDown());
    }


    @Override // 아군 전체 방어, 뎀컷, 디스펠가드
    public void secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move secondAbility = mainActor.getActor().getMoves().get(MoveType.SECOND_ABILITY);
        // 스테이터스 적용
        commonStatusLogic.setStatusToActors(mainActor, enemy, partyMembers, secondAbility);
        // 스테이터스 계산값 적용
        partyMembers.forEach(partyMember -> commonLogic.syncStatus(partyMember, commonLogic.getStatusEffectMap(partyMember)));

        mainActor.setSecondAbilityCoolDown(secondAbility.getCoolDown());
    }

    @Override // 자신 요다메상승, 통상공격실행 (레코데이션 효과중 전체화)
    public void thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move thirdAbility = mainActor.getActor().getMoves().get(MoveType.THIRD_ABILITY);
        // 스테이터스 적용
        if (commonLogic.hasUniqueStatus(mainActor.getBattleStatuses(), "레코데이션 싱크")) {
            // 레코데이션 효과중 전체화
            commonStatusLogic.setStatusToManualTargets(partyMembers, thirdAbility);
        } else {
            commonStatusLogic.setStatusToActors(mainActor, enemy, partyMembers, thirdAbility);
        }
        // 스테이터스 계산값 적용
        partyMembers.forEach(partyMember -> commonLogic.syncStatus(partyMember, commonLogic.getStatusEffectMap(partyMember)));

        mainActor.setThirdAbilityCoolDown(thirdAbility.getCoolDown());

        // TODO 통상공격 해야된다고 리턴해줘야됨.
    }

    @Override // 데미지, 자신이 레코데이션 싱크중 배율 증가
    public void chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move chargeAttack = mainActor.getActor().getMoves().get(MoveType.CHARGE_ATTACK);
        if (commonLogic.hasUniqueStatus(mainActor.getBattleStatuses(), "레코데이션 싱크")) {
            // 레코데이션 싱크중 배율 극대
            damageLogic.processChargeAttack(mainActor, enemy, 12.5);
        } else {
            damageLogic.processChargeAttack(mainActor, enemy, chargeAttack.getDamageRate());
        }

        // TODO 1어빌 발동
    }

    @Override
    public void firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {

    }

    @Override
    public void secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {

    }

    @Override
    public void thirdSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {

    }

    @Override
    public void postProcessOtherMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {

    }

    @Override
    public void postProcessEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // TODO 적의 무브 결과를 받아와서 해야될듯.
        // 델타레벨 증가확인
        Move secondSupportAbility = mainActor.getActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY);
        commonStatusLogic.setStatusToActors(mainActor, enemy, partyMembers, secondSupportAbility);
        commonLogic.syncStatus(mainActor, commonLogic.getStatusEffectMap(mainActor));
    }

    @Override
    public void onBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        commonInitLogic.initBattleCharacter(mainActor, commonLogic.getStatusEffectMap(mainActor));
    }

    @Override
    public void onTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        boolean isAlphaLevelMax = commonLogic.isUniqueStatusReachedLevel(mainActor.getBattleStatuses(), "알파", 4);
        boolean isDeltaLevelMax = commonLogic.isUniqueStatusReachedLevel(mainActor.getBattleStatuses(), "델타", 4);
        if (isDeltaLevelMax && isAlphaLevelMax) {
            // 알파와 델타레벨이 모두 최고레벨일때 자신에게 서폿3 레코데이션 싱크 적용
            Move thirdSupportAbility = mainActor.getActor().getMoves().get(MoveType.THIRD_SUPPORT_ABILITY);
            commonStatusLogic.setStatusToActors(mainActor, enemy, partyMembers, thirdSupportAbility);
            commonLogic.syncStatus(mainActor, commonLogic.getStatusEffectMap(mainActor));

            // 자신을 제외한 아군 전체에게 서폿 1, 2 알파와 델타 레벨 적용
            Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
            Move secondSupportAbility = mainActor.getActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY);
            partyMembers.remove(mainActor);
            commonStatusLogic.setStatusToManualTargets(partyMembers, firstSupportAbility);
            commonStatusLogic.setStatusToManualTargets(partyMembers, secondSupportAbility);

            // 레벨 4로 변경 및 실적용
            List<BattleStatus> battleStatuses = partyMembers.stream().map(BattleActor::getBattleStatuses).flatMap(List::stream).toList();
            commonLogic.addUniqueStatusLevel(battleStatuses, List.of("알파", "델타"), 3);
            partyMembers.forEach(partyMember -> commonLogic.syncStatus(partyMember, commonLogic.getStatusEffectMap(partyMember)));
            
            // 자신의 3어빌 쿨타임 0으로 감소
            mainActor.setFirstAbilityCoolDown(0);


        }
    }
}
