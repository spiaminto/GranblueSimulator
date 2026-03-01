package com.gbf.granblue_simulator.battle.logic.move;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.battle.logic.util.StatusUtil;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MoveLogicCheckCondition {

    private final BattleContext battleContext;

    /**
     * moveType 행동 시
     *
     * @param moveType NORMAL_ATTACK, CHARGE_ATTACK_DEFAULT, FIRST_SUPPORT_ABILITY, ...
     */
    public boolean isMoveType(MoveLogicResult otherResult, MoveType moveType) {
        return otherResult != null && otherResult.getMove().getType() == moveType;
    }

    /**
     * 자신이 [더블 어택 / 트리플 어택] 시
     *
     * @param attackCount    더블어택: 2, 트리플어택: 3
     * @return
     */
    public boolean isNormalAttackAndAttackCountIs(MoveLogicResult otherResult, int attackCount) {
        return otherResult != null && otherResult.getMove().getType() == MoveType.NORMAL_ATTACK && otherResult.getNormalAttackCount() == attackCount;
    }

    /**
     * 자신이 attackCount 초과 횟수만큼 일반 공격시 (자신이 연속공격시)
     * @param attackCount
     */
    public boolean isNormalAttackAndAttackCountGreaterThan(MoveLogicResult otherResult, int attackCount) {
        return otherResult != null && otherResult.getMove().getType() == MoveType.NORMAL_ATTACK && otherResult.getNormalAttackCount() > attackCount;
    }

    // 자신이 연속공격시...

    /**
     * parentMoveType 행동 시 (트리거)
     *
     * @param otherResult
     * @param moveType    MoveType.parentType 사용, ATTACK, ABILITY, CHARGE_ATTACK,
     * @return
     */
    public boolean isMoveParentType(MoveLogicResult otherResult, MoveType moveType) {
        return otherResult != null && otherResult.getMove().getType().getParentType() == moveType;
    }

    /**
     * parentMoveType 행동 시 (트리거)
     *
     * @param commandAbilityId 커맨드 어빌리티 id from battleContext
     */
    public boolean isUsedAbility(MoveLogicResult otherResult, Long commandAbilityId) {
        return otherResult != null && otherResult.getMove().getType().getParentType() == MoveType.ABILITY && otherResult.getMove().getId().equals(commandAbilityId);
    }

    /**
     * parentMoveType 행동 시 (트리거)
     *
     * @param otherResult
     * @param moveType    MoveType.parentType 사용, ATTACK, ABILITY, CHARGE_ATTACK,
     * @return
     */
    public boolean isMoveParentType(MoveLogicResult otherResult, MoveType... moveType) {
        for (MoveType type : moveType) {
            if (isMoveParentType(otherResult, type)) return true;
        }
        return false;
    }

    /**
     * 특정 move 발동시 자동발동 <br> 
     * 예를들어 첫번째 서포트 어빌리티가 "XX 시 자신의 두번째 어빌리티 자동발동" 일때, 두번째 어빌리티 발동시 첫번째 서포트 어빌리티로 부터 트리거 되었는지 확인하기위해 사용
     * @param logicId 트리거 한 move 의 logicId
     */
    public boolean isTriggeredByLogicId(MoveLogicResult otherResult, String logicId) {
        return otherResult != null && logicId.equals(otherResult.getMove().getBaseMove().getLogicId());
    }

    /**
     * standbyType 전조 해제시
     *
     * @param otherResult
     * @param standbyType
     * @return
     */
    public boolean isEnemyBreak(MoveLogicResult otherResult, MoveType standbyType) {
        OmenResult omenResult = otherResult.getOmenResult();
        return omenResult != null && omenResult.isOmenBreak() && omenResult.getStandbyMoveType() == standbyType;
    }

    /**
     * 자신이 적의 공격 타겟이 될 시
     *
     * @param otherResult
     * @param self
     * @return
     */
    public boolean isTargetedByEnemy(MoveLogicResult otherResult, Actor self) {
        return !otherResult.getEnemyAttackTargets().isEmpty() && otherResult.getEnemyAttackTargets().contains(self);
    }

    /**
     * 자신이 적에게 피 데미지시
     *
     * @param otherResult
     * @param self
     * @return
     */
    public boolean isDamagedByEnemy(MoveLogicResult otherResult, Actor self) {
        for (int i = 0; i < otherResult.getEnemyAttackTargets().size(); i++) {
            if (otherResult.getDamages().get(i) > 0 && otherResult.getEnemyAttackTargets().get(i).getId().equals(self.getId()))
                return true;
        }
        return false;
    }

    /**
     * effectName 효과 중
     *
     * @param actor
     * @param effectName
     * @return
     */
    public Optional<StatusEffect> hasEffect(Actor actor, String effectName) {
        return StatusUtil.getEffectByName(actor, effectName);
    }

    /**
     * effectName 효과 중 (같은 이름의 효과 여러개일때)
     *
     * @param actor
     * @param effectName
     * @return
     */
    public List<StatusEffect> hasEffects(Actor actor, String effectName) {
        return StatusUtil.getEffectsByName(actor, effectName);
    }

    /**
     * effectName 효과가 레벨 minLevel 이상일때
     * @param minLevel   inclusive
     * @return
     */
    public Optional<StatusEffect> hasEffectLevel(Actor actor, String effectName, int minLevel) {
        return this.hasEffect(actor, effectName)
                .filter(statusEffect -> statusEffect.getLevel() >= minLevel);
    }

    /**
     * effectName 효과가 최고레벨 일때
     */
    public boolean isEffectMaxLevel(Actor actor, String effectName) {
        return this.hasEffect(actor, effectName)
                .filter(statusEffect -> statusEffect.getLevel() >= statusEffect.getBaseStatusEffect().getMaxLevel())
                .isPresent();
    }

    /**
     * effectName 효과 부여시 / 제거시
     *
     * @param effects
     * @param effectName
     * @return
     */
    public boolean hasEffectInResult(List<StatusEffectDto> effects, String effectName) {
        return effects.stream().anyMatch(statusEffectDto -> statusEffectDto.getName().equals(effectName));
    }

    /**
     * effectName 효과를 부여하는 행동시
     *
     * @param move
     * @param effectName
     * @return
     */
    public boolean hasEffectInMove(Move move, String effectName) {
        return move.getBaseMove().getBaseStatusEffects().stream().anyMatch(baseEffect -> baseEffect.getName().equals(effectName));
    }

    /**
     * target 의 강화효과 해제시 <br>
     * 매핑 쉽게하려고 Optional 로 만들었는데 좀 더 지켜봐야할듯
     *
     * @param defaultMoveLogicResult
     * @param target
     * @return
     */
    public Optional<DefaultMoveLogicResult> targetDispelled(DefaultMoveLogicResult defaultMoveLogicResult, Actor target) {
        return defaultMoveLogicResult.getSetStatusEffectResult().getResults().get(target.getId()).getRemovedStatusEffects().stream()
                .anyMatch(statusEffectDto -> statusEffectDto.getType() == StatusEffectType.BUFF && statusEffectDto.getDuration() > 0)
                ? Optional.of(defaultMoveLogicResult)
                : Optional.empty();
    }

    /**
     * minCount 회 이상 공격행동 시
     *
     * @param actor
     * @param minCount
     * @return
     */
    public boolean executedStrikeMoreThan(Actor actor, int minCount) {
        return actor.getExecutedStrikeCount() >= minCount;
    }


    public boolean isAttackMove(BaseMove move) {
        return move.getParentType() == MoveType.ATTACK
                || move.getType() == MoveType.CHARGE_ATTACK_DEFAULT;
    }

    /**
     * 자동 발동 시<br>
     * 일반 어빌리티가 자동발동도 가능할시, 이쪽으로 검사
     *
     * @param request
     * @return 자동발동 했다면 true
     */
    public boolean triggered(MoveLogicRequest request) {
        Long commandAbilityId = battleContext.getCommandAbilityId();
        return request.getOtherResult() != null
                || (request.getMove().getType().getParentType() == MoveType.ABILITY && !request.getMove().getId().equals(commandAbilityId));
    }


}
