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
//        moveLogicRegistry.register(abilityKey(gid, 4), this::fourthAbility);
        moveLogicRegistry.register(abilityKey(gid, 5), this::fifthAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move attack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(attack));
    }

    // 영준호걸: 데미지, 자신에게 베리어 10000
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(DefaultMoveRequest.from(chargeAttack)));
    }

    // 팔랑크스: 참전자 전체 50% 데미지컷
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // 노블레스 프로테지 : 1턴간 자신이 감싸기, 베리어 15000 효과
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // 삭제
    // 풀 레지스트 : 아군 전체의 약화효과 내성 100% 상승, 약화효과 1개 회복
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // 세이크리드 프로텍션 : 자신이 감싸기, 피데미지 5000 감소
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // 테르모필레: 적에게 5.0배 데미지 3회. 자신에게 배수 효과
    // -- 자신에게 클리어, 크리티컬 확률 증가 효과 ◆자신이 베리어 효과중 공격행동 후 자동 발동
    protected MoveLogicResult fifthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [BATTLE_START] 방패의 수호: 자신의 현재체력 비율이 감소할수록 방어력 상승 (최대 200%)
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // [SELF_STRIKE_START] 성기사의 긍지: 자신이 감싸기 효과중 공격시 2회행동
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.hasEffect(ability.getActor(), "감싸기").isEmpty()) return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

}
