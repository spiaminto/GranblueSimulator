package com.gbf.granblue_simulator.battle.domain.actor.prop;

import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.metadata.domain.move.MotionType;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
public class Omen {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "base_omen_id")
    private BaseOmen baseOmen;

    @OneToOne @JoinColumn(name = "enemy_id") @ToString.Exclude
    private Enemy enemy;

    @Type(ListArrayType.class)
    @Column(name = "cancel_condition_indexes", columnDefinition = "integer[]")
    private List<Integer> cancelConditionIndexes = new ArrayList<>();

    @Type(ListArrayType.class)
    @Column(name = "remain_values", columnDefinition = "integer[]")
    private List<Integer> remainValues = new ArrayList<>(); // cancelConditionIndexes 와 인덱스 동기화

    @CreationTimestamp
    private LocalDateTime createdAt;

    public MoveType getStandbyType() {
        return this.baseOmen.getStandbyType();
    }

    public MotionType getMotionType() {
        return this.baseOmen.getMotionType();
    }

    public Omen mapEnemy(Enemy enemy) {
        this.enemy = enemy;
        enemy.updateOmen(this);
        return this;
    }

}
