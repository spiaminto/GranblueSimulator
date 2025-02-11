package com.gbf.granblue_simulator.logic.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.logic.character.dto.CharacterLogicResult;
import com.gbf.granblue_simulator.logic.common.CalcStatusLogic;
import com.gbf.granblue_simulator.logic.common.DamageLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.repository.BattleStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PaladinLogic implements CharacterLogic {

    private final CalcStatusLogic calcStatusLogic;
    private final SetStatusLogic setStatusLogic;
    private final DamageLogic damageLogic;
    private final Long id = 1L;

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

        return CharacterLogicResult.builder()
                .statusList(List.of())
                .damages(damageLogicResult.getDamages())
                .additionalDamages(damageLogicResult.getAdditionalDamages())
                .moveType(moveType)
                .build();
    }

    @Override // 아군전체 데미지 컷
    public CharacterLogicResult firstAbility(BattleActor paladin, BattleActor enemy, List<BattleActor> partyMembers) {
        Move ability = paladin.getActor().getMoves().get(MoveType.FIRST_ABILITY);
        // 스테이터스 적용
        setStatusLogic.setStatus(paladin, enemy, partyMembers, ability);
        // 스테이터스 계산값 적용
        partyMembers.forEach(calcStatusLogic::syncStatus);

        
        // TODO 참전자 스테이터스 적용

        // 쿨타임 적용
        paladin.setFirstAbilityCoolDown(ability.getCoolDown());

        return CharacterLogicResult.builder()
                .statusList(ability.getStatuses())
                .build();
    }

    @Override // 자기자신 감싸기, 베리어
    public CharacterLogicResult secondAbility(BattleActor paladin, BattleActor enemy, List<BattleActor> partyMembers) {
        Move ability = paladin.getActor().getMoves().get(MoveType.SECOND_ABILITY);
        // 스테이터스 적용
        setStatusLogic.setStatus(paladin, enemy, partyMembers, ability);
        // 스테이터스 계산값 적용
        calcStatusLogic.syncStatus(paladin);
        // 쿨타임 적용
        paladin.setSecondAbilityCoolDown(ability.getCoolDown());

        return CharacterLogicResult.builder()
                .statusList(ability.getStatuses())
                .build();
    }

    @Override // 아군전체 피데미지 감소
    public CharacterLogicResult thirdAbility(BattleActor paladin, BattleActor enemy, List<BattleActor> partyMembers) {
        Move ability = paladin.getActor().getMoves().get(MoveType.THIRD_ABILITY);
        // 스테이터스 적용
        setStatusLogic.setStatus(paladin, enemy, partyMembers, ability);
        // 스테이터스 계산값 적용
        partyMembers.forEach(calcStatusLogic::syncStatus);
        // 쿨타임 적용
        paladin.setThirdAbilityCoolDown(ability.getCoolDown());

        return CharacterLogicResult.builder()
                .statusList(ability.getStatuses())
                .build();
    }

    @Override // 아군 전체 스트렝스, 베리어
    public void chargeAttack(BattleActor paladin, BattleActor enemy, List<BattleActor> partyMembers) {
        Move chargeAttack = paladin.getActor().getMoves().get(MoveType.CHARGE_ATTACK);
        // 데미지 계싼
        DamageLogicResult damageLogicResult = damageLogic.processChargeAttack(paladin, enemy, chargeAttack.getDamageRate());

        // 스테이터스 적용
        setStatusLogic.setStatus(paladin, enemy, partyMembers, chargeAttack);
        // 스테이터스 계산값 적용 - 스트렝스, 베리어
        partyMembers.forEach(battleActor -> calcStatusLogic.syncStatus(battleActor));

        //        return CharacterLogicResult.builder()
//                .statusList(chargeAttack.getStatuses())
//                .damages(damageLogicResult.getDamages())
//                .build();
    }

    @Override
    public void firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // onBattleStart 로 갈음
    }

    @Override
    public void secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // onBattleStart 로 갈음
    }

    @Override
    public void thirdSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 없음
    }

    @Override
    public void postProcessOtherMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 없음
    }

    @Override
    public void postProcessEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 없음
    }

    @Override
    public void onBattleStart(BattleActor paladin, BattleActor enemy, List<BattleActor> partyMembers) {
        calcStatusLogic.initStatus(paladin);

        Map<MoveType, Move> moves = paladin.getActor().getMoves();
        List<Status> supportStatuses = new ArrayList<>();
        supportStatuses.addAll(moves.get(MoveType.FIRST_SUPPORT_ABILITY).getStatuses());
        supportStatuses.addAll(moves.get(MoveType.SECOND_SUPPORT_ABILITY).getStatuses());
        setStatusLogic.setStatus(paladin, enemy, partyMembers, moves.get(MoveType.FIRST_SUPPORT_ABILITY));
        setStatusLogic.setStatus(paladin, enemy, partyMembers, moves.get(MoveType.SECOND_SUPPORT_ABILITY));

        //TODO 테스트를 위해 현재 디아스포라는 로직없이 여기서 init 함
        calcStatusLogic.initStatus(enemy);
    }

    @Override
    public void onTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {

    }

}
