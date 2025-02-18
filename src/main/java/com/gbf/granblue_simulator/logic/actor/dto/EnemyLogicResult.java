package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class EnemyLogicResult {

    // 필수
    private Long mainBattleActorId;
    private MoveType moveType;
    @Builder.Default
    private List<Integer> damages = new ArrayList<>();

    // 비필수, 중요
    private Integer totalHitCount; // 통상공격의 경우 추격까지 모두 더함
    @Builder.Default
    private List<List<Integer>> additionalDamages = new ArrayList<>();
    @Builder.Default
    private List<Status> statusList = new ArrayList<>();
    @Builder.Default
    private List<Integer> chargeGauges = new ArrayList<>(); // order by BattleActor.currentOrder
    private Integer enemyChargeGauge;

    // 필요한 경우 사용
    @Builder.Default
    private List<Long> EnemyChargeAttackTargetIds = new ArrayList<>(); // 적의 특수기 공격타겟

    @Accessors(fluent = true)
    private boolean hasNextMove; // 후행동
    private MoveType nextMoveType;
    private StatusTargetType nextMoveTarget;

}
