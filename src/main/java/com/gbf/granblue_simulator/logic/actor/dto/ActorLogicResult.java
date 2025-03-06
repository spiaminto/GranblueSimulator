package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelCond;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class ActorLogicResult {

    // 필수
    private Long mainBattleActorId;
    private Integer mainBattleActorOrder;
    private MoveType moveType;
    @Builder.Default
    private List<Integer> damages = new ArrayList<>();

    // 프론트 갱신용
    // 0: enemy , 1~: character (index 는 currentOrder)
    @Builder.Default
    private List<Integer> hpList = new ArrayList<>();
    @Builder.Default
    private List<Integer> chargeGauges = new ArrayList<>();
    @Builder.Default
    private List<List<BattleStatus>> addedBattleStatusesList = new ArrayList<>();
    @Builder.Default
    private List<List<BattleStatus>> removedBattleStatusesList = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> abilityCooldowns = new ArrayList<>();

    // 비필수, 중요
    private Integer totalHitCount; // 통상공격의 경우 추격까지 모두 더함
    @Builder.Default
    private List<List<Integer>> additionalDamages = new ArrayList<>();
    private Integer enemyChargeGauge;

    private OmenType omenType;
    private Integer omenValue;
    private String omenCancelCondInfo;

    private boolean enemyDispelled;
    private boolean partyMemberDispelled;

    // 적의 데미지 발생시 사용
    @Builder.Default
    private List<Integer> enemyAttackTargetOrders = new ArrayList<>(); // 적의 특수기 공격타겟 currentOrder
    private boolean isAllTarget;

    @Accessors(fluent = true)
    private boolean hasNextMove; // 후행동
    private MoveType nextMoveType;
    private StatusTargetType nextMoveTarget;

}
