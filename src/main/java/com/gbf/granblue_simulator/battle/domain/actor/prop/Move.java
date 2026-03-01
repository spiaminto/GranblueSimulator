package com.gbf.granblue_simulator.battle.domain.actor.prop;


import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import com.gbf.granblue_simulator.metadata.domain.move.TriggerType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
public class Move {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private MoveType type;

    @Enumerated(EnumType.STRING)
    private TriggerType triggerType;

    @ManyToOne
    @JoinColumn(name = "base_move_id")
    private BaseMove baseMove;

    @ManyToOne
    private Actor actor;

    private int cooldown; // 실시간 쿨다운

    private int currentTurnUseCount; // 현재턴 사용 카운트
    private int useCount; // 전체 useCount

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<TrackingCondition, Object> conditionTracker = new HashMap<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * 임시 사용을 위해 set
     *
     * @param actor
     * @return
     */
    public Move setActor(Actor actor) {
        this.actor = actor;
        return this;
    }

    /**
     * 저장을 위해 양방향 매핑 <br>
     * 내부에서 actor.addMove() 처리함
     *
     * @param actor
     * @return
     */
    public Move mapActor(Actor actor) {
        this.actor = actor;
        this.actor.addMove(this);
        return this;
    }

    /**
     * 타입을 런타임에서 직접 매핑<br>
     * BaseMove.MoveType 을 무시하고 해당 타입을 사용하도록 변경됨<br>
     * BaseMove -> Move 변환, 적 전조 발생시 사용중
     *
     * @param moveType 지정할 타입, null 들어올시 무시 (Base.MoveType 을 사용하게 됨)
     */
    public Move mapType(MoveType moveType) {
        if (moveType == null) return this;
        this.type = moveType;
        return this;
    }

    /**
     * 트리거 타입을 런타임에서 직접 매핑<br>
     * BaseMove.TriggerType 을 무시하고 해당 타입을 사용하도록 변경됨<br>
     * 일부 트리거 Move 변환시 사용중
     *
     * @param triggerType 지정할 타입, null 들어올시 무시
     */
    public Move mapTriggerType(TriggerType triggerType) {
        if (triggerType == null) return this;
        this.triggerType = triggerType;
        return this;
    }

    /**
     * conditionTracker를 런타임에서 직접 매핑<br>
     * BaseMove.conditionTracker 을 무시하고 해당 타입을 사용하도록 변경됨<br>
     * 일부 트리거 Move 변환시 사용중
     *
     * @param conditionTracker 지정할 트래커, null 들어올시 무시
     */
    public Move mapConditionTracker(Map<TrackingCondition, Object> conditionTracker) {
        if (conditionTracker == null) return this;
        this.conditionTracker = conditionTracker;
        return this;
    }

    public void modifyCooldown(int modifier) {
        this.cooldown = Math.max(this.cooldown + modifier, 0);
    }

    public void updateCooldown(int cooldown) {
        this.cooldown = Math.max(cooldown, 0);
    }

    public void increaseUseCount() {
        this.useCount++;
        this.currentTurnUseCount++;
    }

    public void clearCurrentTurnUseCount() {
        this.currentTurnUseCount = 0;
    }

    /**
     * BaseMove -> Move 변환 및 저장용, mapActor 필요
     *
     * @param baseMove
     * @return
     */
    public static Move fromBaseMove(BaseMove baseMove) {
        Move move = Move.builder()
                .type(baseMove.getType())
                .triggerType(baseMove.getTriggerType())
                .cooldown(0) // baseMove.getStartCoolDown
                .useCount(0)
                .currentTurnUseCount(0)
                .baseMove(baseMove)
                .conditionTracker(new HashMap<>())
                .build();
        if (baseMove.getConditionTracker() != null) {
            for (TrackingCondition key : baseMove.getConditionTracker().keySet()) {
                move.conditionTracker.put(key, 0);
            }
        }
        return move;
    }

    /**
     * DB 에 저장하지 않을 Move
     */
    public static Move getTransientMove(Actor actor, MoveType type) {
        return Move.builder()
                .type(type)
                .baseMove(BaseMove.getTransientMove(type))
                .actor(actor)
                .build();
    }

}
