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

    /**
     * 자신의 level 을 1 증가
     * 일반적인 setStatusLogic 을 타서 레벨이 증가할 경우 사용
     */
    public void increaseLevel() {
        this.level = Math.min(this.status.getMaxLevel(), this.level + 1);
        this.iconSrc = this.status.getIconSrcs().get(level - 1); // 아이콘이 없으면 에러남
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 들어온 level 값 만큼 자신의 level 을 추가 및 아이콘 url 갱신
     * 이 메서드는 주로 사용자에게 반환 결과 없이 임의로 레벨을 조작할때 사용함.
     * @param level
     * @param updateIcon 아이콘 url 갱신여부
     */
    public void addLevel(int level, boolean updateIcon) {
        this.level = Math.min(this.status.getMaxLevel(), this.level + level);
        this.iconSrc = updateIcon ? this.status.getIconSrcs().get(this.level - 1) : this.iconSrc;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 들어온 level 값 만큼 자신의 level 을 감소 및 url 갱신
     * @param level
     * @param updateIcon
     */
    public void subtractLevel(int level, boolean updateIcon) {
        this.level = Math.max(0, this.level - level);
        this.iconSrc = updateIcon && this.level > 0 ? this.status.getIconSrcs().get(this.level - 1) : this.iconSrc;
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

    /**
     * 스테이터스 효과시간을 연장
     * @param duration 연장할 턴 수
     */
    public void addDuration(int duration) {
        this.duration += duration;
    }

    public void decreaseDuration() {
        this.duration = Math.max(0, this.duration - 1);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 영속인지 확인 (영속은 기본 9999)
     * @return
     */
    public boolean isPerpetual() {
        return this.duration > 9000;
    }
}
