package com.gbf.granblue_simulator.battle.logic.move.dto;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import lombok.*;

import java.util.Arrays;
import java.util.List;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class SetEffectRequest {

    @Singular
    private final List<BaseStatusEffect> baseStatusEffects;
    private final List<Actor> selectedTargets; // nullable
    private final List<Actor> enemyAttackTargets; // nullable

    @Builder.Default // 특정 레벨을 지정해서 효과 부여
    private final int toLevel = 0;

    @Builder.Default // 리필식인지 여부
    private final boolean isRefill = false;

    public static SetEffectRequest of(BaseStatusEffect... baseStatusEffects) {
        return SetEffectRequest.builder()
                .baseStatusEffects(Arrays.asList(baseStatusEffects))
                .build();
    }

    public static SetEffectRequest ofList(List<BaseStatusEffect> baseStatusEffects) {
        return SetEffectRequest.builder()
                .baseStatusEffects(baseStatusEffects)
                .build();
    }

    /**
     * BaseStatusEffect.targetType 을 무시하고 selectedTargets 에 직접 효과를 부여
     */
    public static SetEffectRequest withSelectedTargets(List<BaseStatusEffect> baseStatusEffects, List<Actor> selectedTargets) {
        return SetEffectRequest.builder()
                .baseStatusEffects(baseStatusEffects)
                .selectedTargets(selectedTargets)
                .build();
    }

    /**
     * 효과 부여시 StatusTargetType.PARTY_MEMBERS 의 부여 대상을 적의 공격 타겟(enemyAttackTargets)으로 변경
     */
    public static SetEffectRequest withEnemyTargets(List<BaseStatusEffect> baseStatusEffects, List<Actor> enemyAttackTargets) {
        return SetEffectRequest.builder()
                .baseStatusEffects(baseStatusEffects)
                .enemyAttackTargets(enemyAttackTargets)
                .build();
    }

}
