package com.gbf.granblue_simulator.battle.logic.move.dto;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ResultMapperRequest {

    private Move move;
    private DamageLogicResult damageLogicResult;
    private SetStatusEffectResult setStatusEffectResult;

    private ForMemberAbilityInfo forMemberAbilityInfo;

    private ExecuteOptions executeOptions;

    public static ResultMapperRequest from(Move move) {
        return ResultMapperRequest.builder()
                .move(move)
                .executeOptions(ExecuteOptions.empty())
                .build();
    }

    public static ResultMapperRequest of(Move move, SetStatusEffectResult setStatusEffectResult) {
        return ResultMapperRequest.builder()
                .move(move)
                .setStatusEffectResult(setStatusEffectResult)
                .executeOptions(ExecuteOptions.empty())
                .build();
    }

    public static ResultMapperRequest of(Move move, DamageLogicResult damageLogicResult, SetStatusEffectResult setStatusEffectResult) {
        return ResultMapperRequest.builder()
                .move(move)
                .damageLogicResult(damageLogicResult)
                .setStatusEffectResult(setStatusEffectResult)
                .executeOptions(ExecuteOptions.empty())
                .build();
    }

    public static ResultMapperRequest of(Move move, DamageLogicResult damageLogicResult, SetStatusEffectResult setStatusEffectResult, ExecuteOptions executeOptions) {
        return ResultMapperRequest.builder()
                .move(move)
                .damageLogicResult(damageLogicResult)
                .setStatusEffectResult(setStatusEffectResult)
                .executeOptions(executeOptions)
                .build();
    }

    @Builder
    @Getter
    public static class ExecuteOptions { // not null
        private boolean executeChargeAttack;
        private StatusEffectTargetType executeAttackTargetType;
        private boolean isUnionSummon;
        private boolean isEnemyFormChange;

        public static ExecuteOptions empty() {
            return ExecuteOptions.builder()
                    .executeChargeAttack(false)
                    .executeAttackTargetType(null)
                    .isUnionSummon(false)
                    .isEnemyFormChange(false)
                    .build();
        }

        public static ExecuteOptions chargeAttack() {
            return ExecuteOptions.builder()
                    .executeChargeAttack(true)
                    .build();
        }

        public static ExecuteOptions attack(StatusEffectTargetType executeTargetType) {
            return ExecuteOptions.builder()
                    .executeAttackTargetType(executeTargetType)
                    .build();
        }

        public static ExecuteOptions unionSummon() {
            return ExecuteOptions.builder()
                    .isUnionSummon(true)
                    .build();
        }

        public static ExecuteOptions enemyFormChange() {
            return ExecuteOptions.builder()
                    .isEnemyFormChange(true)
                    .build();
        }


    }

}
