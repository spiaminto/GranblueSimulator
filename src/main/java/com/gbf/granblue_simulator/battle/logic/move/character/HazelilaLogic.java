package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TriggerType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional
@Slf4j
public class HazelilaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040168000";

    protected HazelilaLogic(CharacterMoveLogicDependencies dependencies) {
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
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        moveLogicRegistry.register(triggerAbilityKey(gid, 1), this::firstTriggeredAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 이루지온 포르몬트: 4턴간 아군 전체에 달빛 효과 [/]자신의 3번째 어빌리티 쿨타임 초기화
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        chargeAttack.getActor().getFirstMove(MoveType.THIRD_ABILITY).updateCooldown(0);
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 보아즈: 적의 공격력, 약화효과 내성 감소 - 6턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 야힌: 자신과 남은체력 비율이 가장 낮은 아군의 체력 5000 회복, 베리어 효과 - 6턴
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 페이즈 오브 더 문: 아군전체의 오의 게이지 20% 상승 [/]2턴간 연속공격 확률 상승
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 루나틱 벤데타: 자신에게 월광의 거울빛 효과 부여
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        saveTriggeredMove(List.of(ability.getActor()), triggerAbilityKey(this.gid, 1));
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_CHARACTER] 월광의 거울빛 효과: 아군이 연속공격시 1.5배 X 3회 데미지
    protected MoveLogicResult firstTriggeredAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (!checkCondition.isMoveType(request.getOtherResult(), MoveType.NORMAL_ATTACK)
                || !checkCondition.isNormalAttackAndAttackCountGreaterThan(request.getOtherResult(), 1)) {
            return resultMapper.emptyResult();
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [BATTLE_START] 여왕의 정위치: 홀수턴마다 자신이 오의 재발동(1회)
    // 짝수턴 종료시 자신에게 오의재발동 효과
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

}
