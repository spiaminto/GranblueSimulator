package com.gbf.granblue_simulator.domain.actor.battle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;


@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString(exclude = {"battleActor", "status"})
public class BattleStatus {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer duration; // 효과 시간 (실시간)
    private Integer level; // 레벨 (실시간)
    private String iconSrc; // 아이콘 SRC, status 로부터 받아 초기화 하며 레벨이 증가할때마다 마지막 글자의 숫자가 증가함.

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "battle_actor_id")
    private BattleActor battleActor;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "status_id")
    private Status status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public BattleStatus setBattleActor(BattleActor battleActor) {
        this.battleActor = battleActor;
        battleActor.getBattleStatuses().add(this);
        return this;
    }

    public void increaseLevel() {
        this.level = Math.min(this.status.getMaxLevel(), this.level + 1);
        // 레벨이 10 이상일경우 버그남. 레벨은 무조건 9 까지. <- 이제 src 배열로 저장하니까 상관없지 않나?
        this.iconSrc = this.status.getIconSrcs().get(level - 1);
        this.updatedAt = LocalDateTime.now();
    }

    public void addLevel(int level) {
        this.level = Math.min(this.status.getMaxLevel(), this.level + level);
        this.iconSrc = this.status.getIconSrcs().get(level - 1);
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isMaxLevel() {
        return this.level >= this.status.getMaxLevel();
    }

    /**
     * 같은 버프가 다시걸렷을때 효과시간 초기화
     */
    public void resetDuration() {
        this.duration = this.status.getDuration();
        this.updatedAt = LocalDateTime.now();
    }

    public void decreaseDuration() {
        this.duration = Math.max(0, this.duration - 1);
        this.updatedAt = LocalDateTime.now();
    }
}
