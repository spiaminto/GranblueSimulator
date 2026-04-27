package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EuropaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040190000";
    private final BaseMoveService baseMoveService;
    private final MoveService moveService;

    protected EuropaLogic(CharacterMoveLogicDependencies dependencies, BaseMoveService baseMoveService, MoveService moveService) {
        super(dependencies);
        this.baseMoveService = baseMoveService;
        this.moveService = moveService;
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

    // 토라스 블론테: 적에게 빙결 효과 [/]4턴간 아군 전체의 트리플 어택 확률 50% 상승
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 마나 블래스트: 1배 5회, 적에게 빙결 효과 [/] 아군 전체의 약화효과 1개, 체력 3000 회복 - 4턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 튜로스 아지리스: 4턴간 아군 전체에 피격데미지 화속성 변환, 화속성 피격데미지 감소 효과, 약화효과 내성 100% 상승 - 8턴
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability)); // 기본 효과 처리 반환
    }

    // 플레아데스: 5.5배 2회, 2턴간 자신의 트리플어택 확률상승, 분할데미지(2회) 효과 ◆적이 빙결레벨 7 이상일때, 히트수 2배
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        int hitCount = checkCondition.hasEffectLevel(battleContext.getEnemy(), "빙결", 7)
                .map(effect -> ability.getBaseMove().getHitCount() * 2)
                .orElse(ability.getBaseMove().getHitCount());
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withHitCount(ability, hitCount)));
    }

    // 에우크라톤 포스: 자신과 자신의 다음에 배치된 캐릭터에게 2회행동 효과 ◆자신의 성화 레벨이 10일때만 사용가능 - 5턴
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        if (checkCondition.hasEffectLevel(self, "성화", 10).isEmpty())
            throw new MoveValidationException("성화 레벨이 부족해 어빌리티를 사용할 수 없습니다.");

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_SELF] 성천의 성화: 자신이 트리플 어택 또는 어빌리티 사용시 성화레벨 1 상승.
    // 성화 레벨에 비례해 공격력, 회복력 상승
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        boolean isTripleAttack = checkCondition.isNormalAttackAndAttackCountIs(request.getOtherResult(), 3);
        boolean usedAbility = checkCondition.isUsedAbility(request.getOtherResult(), battleContext.getCommandAbilityId());
        boolean isEffectMaxLevel = checkCondition.isEffectMaxLevel(ability.getActor(), "성화");
        if (!isEffectMaxLevel && (isTripleAttack || usedAbility)) {
            return resultMapper.fromDefaultResult(defaultAbility(ability));
        }
        return resultMapper.emptyResult();
    }

    // [REACT_SELF] 사로잡힌 아름다운 공주: 자신이 트리플 어택시 적에게 5.0배 데미지
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return checkCondition.isNormalAttackAndAttackCountIs(request.getOtherResult(), 3)
                ? resultMapper.fromDefaultResult(defaultAbility(ability))
                : resultMapper.emptyResult();
    }
}
