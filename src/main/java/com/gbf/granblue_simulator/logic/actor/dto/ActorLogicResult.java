package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
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
    private Long mainActorId; // battleActor.actor.id
    private int mainBattleActorOrder;
    private Long targetActorId; // 적의 현재 폼을 확인하기 위해 사용
    private MoveType moveType;

    private Integer currentTurn;

    // 프론트 갱신용
    // 0: enemy , 1~: character (index 는 currentOrder)
    @Builder.Default
    private List<Integer> damages = new ArrayList<>();
    @Builder.Default
    private List<ElementType> damageElementTypes = new ArrayList<>();
    @Builder.Default
    private List<Integer> hps = new ArrayList<>();
    @Builder.Default
    private List<Integer> hpRates = new ArrayList<>();
    @Builder.Default
    private List<Integer> chargeGauges = new ArrayList<>();
    private int fatalChainGauge;
    @Builder.Default
    private List<List<BattleStatus>> addedBattleStatusesList = new ArrayList<>();
    @Builder.Default
    private List<List<BattleStatus>> removedBattleStatusesList = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> abilityCooldowns = new ArrayList<>();
    @Builder.Default
    private List<Integer> heals = new ArrayList<>();

    // 비필수, 중요
    private int totalHitCount; // 통상공격의 경우 추격까지 모두 더함
    private int attackMultiHitCount; // 난격시 난격 카운트
    @Builder.Default
    private List<List<Integer>> additionalDamages = new ArrayList<>();
    private int enemyChargeGauge;

    private OmenType omenType;
    private Integer omenValue;
    private String omenCancelCondInfo;
    private String omenName;
    private String omenInfo;

    // 적의 데미지 발생시 사용
    @Builder.Default
    private List<Integer> enemyAttackTargetOrders = new ArrayList<>(); // 적의 특수기 공격타겟 currentOrder
    private boolean isAllTarget;
    
    // 적 글로벌 효과 사용여부
    private boolean enemyPowerUp;
    private boolean enemyCtMax;

    @Accessors(fluent = true)
    private boolean hasNextMove; // 후행동
    private MoveType nextMoveType;
    private StatusTargetType nextMoveTarget;

}
