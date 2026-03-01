package com.gbf.granblue_simulator.battle.logic.move.character.leader;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import org.springframework.stereotype.Component;

@Component
public class PaladinLogic extends DefaultCharacterMoveLogic {

    private final String gid = "110401";

    protected PaladinLogic(CharacterMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(chargeAttackKey(gid), this::chargeAttack);
        moveLogicRegistry.register(abilityKey(gid, 1), this::firstAbility);
        moveLogicRegistry.register(abilityKey(gid, 2), this::secondAbility);
        moveLogicRegistry.register(abilityKey(gid, 3), this::thirdAbility);
        moveLogicRegistry.register(abilityKey(gid, 4), this::fourthAbility);
        moveLogicRegistry.register(abilityKey(gid, 5), this::fifthAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move attack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(attack));
    }

    // 영준호걸: 데미지, 아군전체 스트랭스 회복
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(DefaultMoveRequest.from(chargeAttack)));
    }

    // 팔랑크스: 참전자 전체 데미지컷
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // 노블레스 프로테지 : 자신이 감싸기, 1턴 피데미지 무효, 베리어 10000
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // 세이크리드 프로텍션 : 아군전체 피데미지 감소, 오의 게이지 50%증가
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // 풀 레지스트 : 아군 전체에 약화내성 상승, 디스펠가드 효과
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // [SELF_STRIKE_END] 테르모필레: 적에게 1.5 배 데미지 5회. 자신에게 클리어, 크리티컬 확률 증가 효과 ◆자신이 베리어 효과중 공격행동 후 자동 발동
    protected MoveLogicResult fifthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request) && checkCondition.hasEffect(ability.getActor(), "베리어").isEmpty()) return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // 전투 시작시 자신에게 방패의 수호 효과 [BATTLE_START]
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // 전투 시작시 자신에게 불사신 효과 [BATTLE_START]
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

}
