package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional
@Slf4j
public class WamdusLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040419000";

    protected WamdusLogic(CharacterMoveLogicDependencies dependencies) {
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
        return resultMapper.fromDefaultResult(defaultAttack(request.getMove()));
    }

    // 히도로조아: 적에게 독 효과, 방어력 감소, 극독레벨 1 상승
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 엠비언트 드레인: 적의 CT 1 감소 [/]자신의 오의게이지 50% 상승 - 8턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 보텍스 아트락스: 1턴간 자신이 반드시 트리플어택, 2회행동, 분할데미지(4회) 효과 - 9턴
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_SELF] 이노센트 톡신: 극독레벨 1 상승
    // ◆적의 극독 Lv7 이상일때, 오의 후 자동발동
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request)) {
            if (checkCondition.hasEffectLevel(battleContext.getEnemy(), "극독", 7).isEmpty() || !checkCondition.isMoveParentType(request.getOtherResult(), MoveType.CHARGE_ATTACK)){
                return resultMapper.emptyResult();
            }
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 『 벽 』의 월운: 자신에게 재생, 벽의 월운 효과
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 루-도- 의 행복: 최대체력과 적대심이 높다 (타겟확률 3배)
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_CHARACTER] 이외의『 벽 』: 페이탈 체인 발동시 아군 전체에 피격 데미지 감소 효과
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.isMoveParentType(request.getOtherResult(), MoveType.FATAL_CHAIN))
            return resultMapper.fromDefaultResult(defaultAbility(ability));
        return resultMapper.emptyResult();
    }

}
