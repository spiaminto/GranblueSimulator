package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
@Slf4j
public class SilviaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040613000";

    protected SilviaLogic(CharacterMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid), this::chargeAttack);
        moveLogicRegistry.register(abilityKey(gid, 1), this::firstAbility);
        moveLogicRegistry.register(abilityKey(gid, 2), this::secondAbility);
        moveLogicRegistry.register(abilityKey(gid, 3), this::thirdAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // D - 오블리탈레이트: 적의 강화효과 1개 제거 [/]자신의 2번째 어빌리티 쿨타임 초기화
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        chargeAttack.getActor().getFirstMove(MoveType.SECOND_ABILITY).updateCooldown(0);
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // [REACT_SELF] 엣지 오브 인포서: 적의 공격력, 특수기데미지 감소(누적) [/]자신의 오의게이지 20% 상승
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request) && !checkCondition.isMoveType(request.getOtherResult(), MoveType.FIRST_SUPPORT_ABILITY))
            return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 크라이시스 퍼세큐트: 아군 전체의 오의 게이지 20% 상승, 체력을 2000 회복
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 월드 컨빅션: 자신의 오의게이지 200% 상승, 2회행동 효과 ◆단죄 Lv5 일때 사용시, 다른 어빌리티의 쿨타임 초기화
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        checkCondition.hasEffectLevel(ability.getActor(), "단죄", 5)
                .ifPresent(effect -> {
                    ability.getActor().getFirstMove(MoveType.FIRST_ABILITY).updateCooldown(0);
                    ability.getActor().getFirstMove(MoveType.SECOND_ABILITY).updateCooldown(0);
                });
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [CHARACTER_STRIKE_ALL_END] 타천사의 감시자: 오의게이지 최대치가 200% [/]오의를 2회이상 사용한 아군의 공격 종료시, 자신의 1번째 어빌리티가 자동발동
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (request.getOtherResult().getMainActor().getStatusDetails().getExecutedChargeAttackCount() < 2)
            return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] 단죄의 사명: 턴 종료시 아군이 5회 이상 오의를 사용했다면, [/]자신의 단죄레벨 1 상승
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        int chargeAttackCount = battleContext.getAllCharacters().stream().mapToInt(actor -> actor.getStatusDetails().getExecutedChargeAttackCount()).sum();
        if (chargeAttackCount < 5) return resultMapper.emptyResult();
        
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }
}
