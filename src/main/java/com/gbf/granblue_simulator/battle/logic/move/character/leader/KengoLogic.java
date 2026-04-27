package com.gbf.granblue_simulator.battle.logic.move.character.leader;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
@Slf4j
public class KengoLogic extends DefaultCharacterMoveLogic {

    private final String gid = "220301";

    protected KengoLogic(CharacterMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid), this::chargeAttack);
        moveLogicRegistry.register(abilityKey(gid, 1), this::firstAbility);
        moveLogicRegistry.register(abilityKey(gid, 2), this::secondAbility);
        moveLogicRegistry.register(abilityKey(gid, 3), this::thirdAbility);
        moveLogicRegistry.register(abilityKey(gid, 4), this::fourthAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 오륜검: 아군 전체의 오의게이지 10% 상승
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 사기 향상: 참전자 전체의 오의게이지 20% 상승
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 검선일여: 자신의 오의게이지 50% 상승, 2턴간 트리플어택 확률 50% 상승
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 비검 - 극일섬: 4턴간 적의 연속공격 확률 감소
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 무명참: 자신의 오의게이지를 40% 소모하여 자신에게 3회행동 효과
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (ability.getActor().getChargeGauge() < 40)
            throw new MoveValidationException("오의게이지가 부족하여 어빌리티를 사용할 수 없습니다.");
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_SELF] 운요: 오의 게이지 최대치가 200% [/]자신이 오의 발동시 운요 레벨 1 상승
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (!checkCondition.isMoveParentType(request.getOtherResult(), MoveType.CHARGE_ATTACK))
            return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] 부동지신: 턴 종료시 아군이 오의를 4회이상 사용했다면, 아군전체의 오의게이지 20% 상승
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        int chargeAttackCount = battleContext.getFrontCharacters().stream()
                .mapToInt(actor -> actor.getStatusDetails().getExecutedChargeAttackCount())
                .sum();
        if (chargeAttackCount < 4) return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

}
