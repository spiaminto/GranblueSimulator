package com.gbf.granblue_simulator.logic.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.character.dto.CharacterLogicResult;
import com.gbf.granblue_simulator.logic.common.CalcStatusLogic;
import com.gbf.granblue_simulator.logic.common.DamageLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.StatusUtil;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@Transactional
@RequiredArgsConstructor
@Slf4j
public class YachimaLogic implements CharacterLogic {
    private final Integer baseFirstAbilityHitCount = 6;
    private final DamageLogic damageLogic;
    private final SetStatusLogic setStatusLogic;
    private final StatusUtil statusUtil;
    private final CalcStatusLogic calcStatusLogic;


    @Override
    public CharacterLogicResult attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        DamageLogicResult damageLogicResult = damageLogic.processAttack(mainActor, enemy);
        int hitCount = damageLogicResult.getDamages().size();
        MoveType moveType = MoveType.NORMAL_ATTACK;
        switch (hitCount) {
            case 1 -> moveType = MoveType.SINGLE_ATTACK;
            case 2 -> moveType = MoveType.DOUBLE_ATTACK;
            case 3 -> moveType = MoveType.TRIPLE_ATTACK;
        }

        Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
        setStatusLogic.setStatus(mainActor, enemy, partyMembers, firstSupportAbility);

        return CharacterLogicResult.builder()
                .statusList(List.of())
                .damages(damageLogicResult.getDamages())
                .additionalDamages(damageLogicResult.getAdditionalDamages())
                .moveType(moveType)
                .build();
    }

    @Override // 적에게 데미지, 자신에게 추격, 적에게 디그레이드스피넬
    public CharacterLogicResult firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move firstAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_ABILITY);

        int alphaLevel = statusUtil.getUniqueStatusLevel(mainActor, "알파");
        int hitCount = baseFirstAbilityHitCount + alphaLevel;
        DamageLogicResult damageLogicResult = damageLogic.processAbilityAttack(mainActor, enemy, firstAbility.getDamageRate(), hitCount);

        // 스테이터스 적용
        setStatusLogic.setStatus(mainActor, enemy, partyMembers, firstAbility);

        // 스테이터스 계산값 적용
        List.of(mainActor, enemy).forEach(calcStatusLogic::syncStatus);

        mainActor.setFirstAbilityCoolDown(firstAbility.getCoolDown());

        return CharacterLogicResult.builder()
                .damages(damageLogicResult.getDamages())
                .statusList(firstAbility.getStatuses())
                .moveType(MoveType.FIRST_ABILITY)
                .build();
    }


    @Override // 아군 전체 방어, 뎀컷, 디스펠가드
    public CharacterLogicResult secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move secondAbility = mainActor.getActor().getMoves().get(MoveType.SECOND_ABILITY);
        // 스테이터스 적용
        setStatusLogic.setStatus(mainActor, enemy, partyMembers, secondAbility);
        // 스테이터스 계산값 적용
        partyMembers.forEach(calcStatusLogic::syncStatus);

        mainActor.setSecondAbilityCoolDown(secondAbility.getCoolDown());

        return CharacterLogicResult.builder()
                .statusList(secondAbility.getStatuses())
                .moveType(MoveType.SECOND_ABILITY)
                .build();
    }

    @Override // 자신 요다메상승, 통상공격실행 (레코데이션 효과중 전체화)
    public CharacterLogicResult thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move thirdAbility = mainActor.getActor().getMoves().get(MoveType.THIRD_ABILITY);
        // 스테이터스 적용
        boolean hasUniqueStatus = statusUtil.hasUniqueStatus(mainActor, "레코데이션 싱크");
        if (hasUniqueStatus) {
            // 레코데이션 효과중 전체화
            setStatusLogic.setStatusToManualTargets(partyMembers, thirdAbility);
        } else {
            setStatusLogic.setStatus(mainActor, enemy, partyMembers, thirdAbility);
        }
        // 스테이터스 계산값 적용
        partyMembers.forEach(calcStatusLogic::syncStatus);

        mainActor.setThirdAbilityCoolDown(thirdAbility.getCoolDown());

        // TODO 통상공격 해야된다고 리턴해줘야됨.
        StatusTargetType afterMoveTarget = hasUniqueStatus ? StatusTargetType.PARTY_MEMBERS : StatusTargetType.SELF;
        return CharacterLogicResult.builder()
                .statusList(thirdAbility.getStatuses())
                .moveType(MoveType.THIRD_ABILITY)
                .hasNextMove(true)
                .nextMoveType(MoveType.NORMAL_ATTACK)
                .nextMoveTarget(afterMoveTarget)
                .build();
    }

    @Override // 데미지, 자신이 레코데이션 싱크중 배율 증가
    public void chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move chargeAttack = mainActor.getActor().getMoves().get(MoveType.CHARGE_ATTACK);
        if (statusUtil.hasUniqueStatus(mainActor, "레코데이션 싱크")) {
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
        setStatusLogic.setStatus(mainActor, enemy, partyMembers, secondSupportAbility);
        calcStatusLogic.syncStatus(mainActor);
    }

    @Override
    public void onBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        calcStatusLogic.initStatus(mainActor);
    }

    @Override
    public void onTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        log.info("mainActor = {}", mainActor);
        boolean isAlphaLevelMax = statusUtil.isUniqueStatusReachedLevel(mainActor, "알파", 4);
        boolean isDeltaLevelMax = statusUtil.isUniqueStatusReachedLevel(mainActor, "델타", 4);
        if (isDeltaLevelMax && isAlphaLevelMax) {
            // 알파와 델타레벨이 모두 최고레벨일때 자신에게 서폿3 레코데이션 싱크 적용
            Move thirdSupportAbility = mainActor.getActor().getMoves().get(MoveType.THIRD_SUPPORT_ABILITY);
            setStatusLogic.setStatus(mainActor, enemy, partyMembers, thirdSupportAbility);
            calcStatusLogic.syncStatus(mainActor);

            // 자신을 제외한 아군 전체에게 서폿 1, 2 알파와 델타 레벨 적용
            Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
            Move secondSupportAbility = mainActor.getActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY);
            partyMembers.forEach(partyMember -> log.info("partyMembers = {}, equals mainActor = {}", partyMember, partyMember.equals(mainActor)));
            List<BattleActor> others = new ArrayList<>(partyMembers);
            boolean remove = others.remove(mainActor);
            log.info("removed = {}", remove);
            setStatusLogic.setStatusToManualTargets(others, firstSupportAbility);
            setStatusLogic.setStatusToManualTargets(others, secondSupportAbility);

            // 레벨 4로 변경 및 실적용
            log.info("others = {}", others);
            others.forEach(other -> log.info("other = {}", other));
            statusUtil.addUniqueStatusLevelAll(others, 3, "알파", "델타");
            partyMembers.forEach(calcStatusLogic::syncStatus);
            
            // 자신의 3어빌 쿨타임 0으로 감소
            mainActor.setFirstAbilityCoolDown(0);


        }
    }
}
