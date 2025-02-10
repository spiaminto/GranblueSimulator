package com.gbf.granblue_simulator.logic.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.logic.*;
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

    private final CommonLogic commonLogic;
    private final CommonInitLogic commonInitLogic;
    private final CommonStatusLogic commonStatusLogic;
    private final DamageLogic damageLogic;
    private final BattleStatusRepository battleStatusRepository;
    private final Long id = 1L;

    @Override
    public void attack(BattleActor paladin, BattleActor enemy, List<BattleActor> partyMembers) {
        DamageLogicResult damageResult = damageLogic.processAttack(paladin, enemy);
    }

    @Override // 아군전체 데미지 컷
    public void firstAbility(BattleActor paladin, BattleActor enemy, List<BattleActor> partyMembers) {
        Move ability = paladin.getActor().getMoves().get(MoveType.FIRST_ABILITY);
        // 스테이터스 적용
        commonStatusLogic.setStatusToActors(paladin, enemy, partyMembers, ability);
        // 스테이터스 계산값 적용
        partyMembers.forEach(battleActor -> commonLogic.setDef(battleActor, commonLogic.getStatusEffectMap(battleActor)));
        
        // TODO 참전자 스테이터스 적용

        // 쿨타임 적용
        paladin.setFirstAbilityCoolDown(ability.getCoolDown());
    }

    @Override // 자기자신 감싸기, 베리어
    public void secondAbility(BattleActor paladin, BattleActor enemy, List<BattleActor> partyMembers) {
        Move ability = paladin.getActor().getMoves().get(MoveType.SECOND_ABILITY);
        // 스테이터스 적용
        commonStatusLogic.setStatusToActors(paladin, enemy, partyMembers, ability);
        // 스테이터스 계산값 적용
        partyMembers.forEach(battleActor -> commonLogic.setDef(battleActor, commonLogic.getStatusEffectMap(battleActor)));
        // 쿨타임 적용
        paladin.setSecondAbilityCoolDown(ability.getCoolDown());
    }

    @Override // 아군전체 피데미지 감소
    public void thirdAbility(BattleActor paladin, BattleActor enemy, List<BattleActor> partyMembers) {
        Move ability = paladin.getActor().getMoves().get(MoveType.THIRD_ABILITY);
        // 스테이터스 적용
        commonStatusLogic.setStatusToActors(paladin, enemy, partyMembers, ability);
        // 스테이터스 계산값 적용
        partyMembers.forEach(battleActor -> commonLogic.setDef(battleActor, commonLogic.getStatusEffectMap(battleActor)));
        // 쿨타임 적용
        paladin.setThirdAbilityCoolDown(ability.getCoolDown());
    }

    @Override // 아군 전체 스트렝스, 베리어
    public void chargeAttack(BattleActor paladin, BattleActor enemy, List<BattleActor> partyMembers) {
        Move chargeAttack = paladin.getActor().getMoves().get(MoveType.CHARGE_ATTACK);
        // 데미지 계싼
        DamageLogicResult damageResult = damageLogic.processChargeAttack(paladin, enemy, chargeAttack.getDamageRate());

        // 스테이터스 적용
        commonStatusLogic.setStatusToActors(paladin, enemy, partyMembers, chargeAttack);
        // 스테이터스 계산값 적용 - 스트렝스, 베리어
        partyMembers.forEach(battleActor -> commonLogic.setAtk(battleActor, commonLogic.getStatusEffectMap(battleActor)));
        partyMembers.forEach(battleActor -> commonLogic.setDef(battleActor, commonLogic.getStatusEffectMap(battleActor)));
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
        Map<MoveType, Move> moves = paladin.getActor().getMoves();
        List<Status> supportStatuses = new ArrayList<>();
        supportStatuses.addAll(moves.get(MoveType.FIRST_SUPPORT_ABILITY).getStatuses());
        supportStatuses.addAll(moves.get(MoveType.SECOND_SUPPORT_ABILITY).getStatuses());
        List<BattleStatus> battleStatuses = supportStatuses.stream().map(status -> BattleStatus.builder().battleActor(paladin).status(status).level(0).build()).toList();
        battleStatusRepository.saveAll(battleStatuses);

        Map<StatusEffectType, List<StatusEffect>> paladinStatusEffects = commonLogic.getStatusEffectMap(paladin);

        //TODO 테스트를 위해 현재 디아스포라는 로직없이 여기서 init 함
        commonInitLogic.initBattleCharacter(paladin, commonLogic.getStatusEffectMap(paladin));
        commonInitLogic.initBattleCharacter(enemy, commonLogic.getStatusEffectMap(enemy));
    }

    @Override
    public void onTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {

    }

}
