package com.gbf.granblue_simulator.domain;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import jakarta.persistence.*;
import lombok.*;

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

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")
    private User user; // 유저 id

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id")
    private Room room;

    @OneToMany(mappedBy = "member") @Builder.Default @ToString.Exclude @EqualsAndHashCode.Exclude
    private List<BattleActor> battleActors = new ArrayList<>();

    private Long partyId; // 입장시 참조 및 검증용으로만 사용. 실시간 참조 x

    @Builder.Default
    private Integer currentTurn = 1; // 현재 자신의 턴, 1부터 시작

    private boolean chargeAttackOn; // 오의 발동 여부, default false
    private boolean chargeAttackSkip; // 오의 스킵 여부, default true

    public void increaseTurn() {
        this.currentTurn++;
    }

    public void setChargeAttackOn(boolean chargeAttackOn) {
        this.chargeAttackOn = chargeAttackOn;
    }

}
