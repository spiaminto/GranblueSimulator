package com.gbf.granblue_simulator.battle.domain;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.user.domain.User;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class Member {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") @ToString.Exclude
    private User user; // 유저 id

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id") @ToString.Exclude
    private Room room;

    @OneToMany(mappedBy = "member") @Builder.Default @EqualsAndHashCode.Exclude @ToString.Exclude
    private List<Actor> actors = new ArrayList<>();

    private Long partyId; // 입장시 참조 및 검증용으로만 사용. 실시간 참조 x

    @Builder.Default
    private int currentTurn = 0; // 현재 자신의 턴, 0부터 시작, 첫입장시 0확인 후 BATTLE_START 때 1로 변경

    private boolean chargeAttackOn; // 오의 발동 여부, default false
    private boolean chargeAttackSkip; // 오의 스킵 여부, default true

    @Type(JsonType.class) @Builder.Default
    @Column(name= "pending_for_all_moves", columnDefinition = "jsonb")
    private List<PendingForAllMove> pendingForAllMoves = new ArrayList<>(); // 대기중인 참전자 버프

    private Long fatalChainMoveId;
    private int fatalChainGauge;

    private int potionCount; // 포션
    private int allPotionCount; // 올포
    
    private int honor; // 공헌도

    @Accessors(fluent = true)
    private boolean usedSummon; // 소환 여부

    private LocalDateTime lastMoveTime; // 마지막 행동 시간
    private int moveCooldown; // 행동 쿨타임, 초 단위

    @Accessors(fluent = true)
    private boolean checkedResult; // 결과 확인여부

    @CreationTimestamp
    private LocalDateTime createdAt;


    public void increaseTurn() {
        this.currentTurn++;
    }

    public void updateChargeAttackOn(boolean chargeAttackOn) {
        this.chargeAttackOn = chargeAttackOn;
    }

    public void updateLastMovedTimeNow() {
        this.lastMoveTime = LocalDateTime.now();
    }

    public void updateMoveCooldown(int moveCooldown) {
        this.moveCooldown = moveCooldown;
    }

    public void updateUsedSummon(boolean usedSummon) {this.usedSummon = usedSummon;}

    public void addPotionCount(int count) {
        this.potionCount += count;
    }

    public void addAllPotionCount(int count) {
        this.allPotionCount += count;
    }

    public void addHonor(int honor) {this.honor += honor;}

    public void updateFatalChainMoveId(Long fatalChainMoveId) {this.fatalChainMoveId = fatalChainMoveId;}

    public void updateFatalChainGauge(int gauge) {this.fatalChainGauge = gauge;}

    public void updateCheckedResult(boolean checkedResult) {this.checkedResult = checkedResult;}


    /**
     * 연관관계 매핑 제외, 사용하지 않도록 하기
     * @return
     */
    public List<Actor> getActors() {
        return this.actors; // usage 확인용
    }

    @Builder
    @Getter @ToString @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    public static class PendingForAllMove {
        private Long moveId;
        private Long sourceMemberId;
        private String sourceUsername;
    }



}
