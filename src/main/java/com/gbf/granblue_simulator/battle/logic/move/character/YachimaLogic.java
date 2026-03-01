package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.SetEffectRequest;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectByName;
import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getLevelByName;

@Component
@Transactional
public class YachimaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040566000";
    private final BaseMoveService baseMoveService;

    protected YachimaLogic(CharacterMoveLogicDependencies dependencies, BaseMoveService baseMoveService) {
        super(dependencies);
        registerLogics();
        this.baseMoveService = baseMoveService;
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid), this::chargeAttack);
        moveLogicRegistry.register(abilityKey(gid, 1), this::firstAbility);
        moveLogicRegistry.register(abilityKey(gid, 2), this::secondAbility);
        moveLogicRegistry.register(abilityKey(gid, 3), this::thirdAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 3), this::thirdSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 4), this::fourthSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move attack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(attack));
    }

    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        // Double damageRate = checkCondition.hasEffect(request.getMove().getActor(), "레코데이션 싱크").isPresent() ? 12.5 : null;
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // [REACT_SELF]
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        if (checkCondition.triggered(request) // 트리거 되었을때, 레코데이션 싱크 효과없으면 발동하지 않음
                && (!checkCondition.isMoveParentType(request.getOtherResult(), MoveType.CHARGE_ATTACK) || checkCondition.hasEffect(self, "레코데이션 싱크").isEmpty())) {
            return resultMapper.emptyResult();
        }

        // 알파레벨에 비례해 히트수 증가
        int hitCount = ability.getBaseMove().getHitCount() + getLevelByName(self, "알파");
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withHitCount(ability, hitCount)));
    }

    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        return resultMapper.fromDefaultResult(defaultAbility(request.getMove()));
    }

    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        StatusEffectTargetType applyEffectTargetType = checkCondition.hasEffect(self, "레코데이션 싱크").isPresent()
                ? StatusEffectTargetType.PARTY_MEMBERS : StatusEffectTargetType.SELF; // 상태효과, 턴진행 없이 통상공격 실행 타겟

        defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, Collections.emptyList()));// 빈 상태효과 전달, 상태효과는 직접적용
        // 타겟에 맞게 상태효과 직접 적용
        List<Actor> statusEffectTargets = applyEffectTargetType == StatusEffectTargetType.SELF ? List.of(self) : battleContext.getFrontCharacters();
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(SetEffectRequest.withSelectedTargets(ability.getBaseMove().getBaseStatusEffects(), statusEffectTargets));

        return resultMapper.toResult(ResultMapperRequest.builder()
                .move(ability)
                .damageLogicResult(null)
                .setStatusEffectResult(setStatusEffectResult)
                .executeOptions(ResultMapperRequest.ExecuteOptions.attack(applyEffectTargetType))
                .build());
    }

    // 자신이 트리플 어택시 사포아비1 적용 (알파레벨 증가) [REACT_SELF]
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        return checkCondition.isNormalAttackAndAttackCountIs(request.getOtherResult(), 3) && !checkCondition.isEffectMaxLevel(request.getMove().getActor(), "알파")
                ? resultMapper.fromDefaultResult(defaultAbility(request.getMove()))
                : resultMapper.emptyResult(); // 자신의 알파레벨이 만렙이면 스킵
    }

    // 자신이 적에게 공격받을시 델타레벨 증가 [ENEMY_STRIKE_END]
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        return checkCondition.isTargetedByEnemy(request.getOtherResult(), request.getMove().getActor()) && !checkCondition.isEffectMaxLevel(request.getMove().getActor(), "델타")
                ? resultMapper.fromDefaultResult(defaultAbility(request.getMove()))
                : resultMapper.emptyResult();
    }

    // 자신이 알파레벨, 델타레벨 최대치인경우 턴 종료시 레코데이션 싱크 효과, 알파 델타를 아군 전체에 적용 [TURN_END]
    protected MoveLogicResult thirdSupportAbility(MoveLogicRequest request) {
        Actor self = request.getMove().getActor();
        if (checkCondition.hasEffect(self, "레코데이션 싱크").isPresent()
                || !checkCondition.isEffectMaxLevel(self, "알파")
                || !checkCondition.isEffectMaxLevel(self, "델타")) {
            return resultMapper.emptyResult();
        }

        // 자신을 포함한 아군 전체에게 알파, 델타 효과 재적용 (타겟 아군 전체로 변경)
        List<BaseMove> supportAbilities = baseMoveService.findByLogicIds(supportAbilityKey(gid, 1), supportAbilityKey(gid, 2));
        List<BaseStatusEffect> baseEffects = supportAbilities.stream()
                .flatMap(move -> move.getBaseStatusEffects().stream())
                .filter(effect -> effect.getName().equals("알파") || effect.getName().equals("델타"))
                .toList();
        setStatusLogic.setStatusEffect(SetEffectRequest.withSelectedTargets(baseEffects, battleContext.getFrontCharacters())); // 이쪽결과는 이펙트 표시 x

        // 재적용 된 알파, 델타 레벨 4로 변경 및 스탯 갱신
        battleContext.getFrontCharacters().forEach(partyMember -> setStatusLogic.addStatusEffectsLevel(
                partyMember, 3,
                getEffectByName(partyMember, "알파").orElse(null),
                getEffectByName(partyMember, "델타").orElse(null)));

        // 자신의 3어빌 쿨타임 0으로 감소
        self.getFirstMove(MoveType.THIRD_ABILITY).updateCooldown(0);
        // 레코데이션 싱크 적용
        return resultMapper.fromDefaultResult(defaultAbility(request.getMove()));
    }

    // 자신이 레코데이션 싱크 효과중 통상공격 후 5배 데미지 3회, 방어력 다운 [REACT_SELF]
    protected MoveLogicResult fourthSupportAbility(MoveLogicRequest request) {
        return checkCondition.isMoveParentType(request.getOtherResult(), MoveType.ATTACK) && checkCondition.hasEffect(request.getMove().getActor(), "레코데이션 싱크").isPresent()
                ? resultMapper.fromDefaultResult(defaultAbility(request.getMove()))
                : resultMapper.emptyResult();
    }


}
