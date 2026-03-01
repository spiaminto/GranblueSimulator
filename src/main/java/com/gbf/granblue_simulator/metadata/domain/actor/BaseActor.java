package com.gbf.granblue_simulator.metadata.domain.actor;

import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn
public abstract class BaseActor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(insertable = false, updatable = false)
    private String dtype;

    @Enumerated(EnumType.STRING)
    private ElementType elementType; // 속성

    private String name;
    private String nameEn; // 영어명

    @Accessors(fluent = true)
    private boolean isLeaderCharacter; // 주인공 여부

    private String weaponId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_move", columnDefinition = "jsonb")
    private MappedMove mappedMove;

    @OneToOne
    @JoinColumn(name = "default_visual_id")
    private ActorVisual defaultVisual;

    @PostLoad
    private void postLoad() {
        if (this.mappedMove != null) {
            this.mappedMove.postLoad();
        }
    }

    // base status
    private int atk;
    private int maxHp;
    private double def; // .1f
    private double doubleAttackRate;
    private double tripleAttackRate;
    private double debuffResistRate;
    private double debuffSuccessRate;
    private double criticalRate;
    private double criticalDamageRate;
    private int maxChargeGauge;
    private double chargeGaugeIncreaseRate;
    private double accuracyRate;
    private double dodgeRate;

    public boolean isEnemy() {
        return "BaseEnemy".equals(this.getDtype());
    }

    public boolean isCharacter() {
        return "BaseCharacter".equals(this.getDtype());
    }

    // TODO insert 관련 추가 수정필요
    public void initCharacterBaseStatus(boolean isLeaderCharacter) {
        this.atk = 7000;
        this.maxHp = 20000;
        this.def = 1.0;
        this.doubleAttackRate = 0.3;
        this.tripleAttackRate = 0.15;
        this.debuffResistRate = 0;
        this.debuffSuccessRate = 1.0;
        this.criticalRate = 0.0;
        this.criticalDamageRate = 0.5;
        this.maxChargeGauge = 100;
        this.chargeGaugeIncreaseRate = 0.0;
        this.accuracyRate = 1.0;
        this.dodgeRate = 0;

        if (isLeaderCharacter) { // 주인공 보정
            this.atk = 10000;
            this.maxHp = 25000;
            this.doubleAttackRate = 0.4;
            this.tripleAttackRate = 0.2;
        }
    }

    public void initEnemyBaseStatus() {
        // 적은 DB 에딧으로 대체
    }

    /**
     * 기본 사용 매핑된 moveId 전부 반환 <br>
     * 주로 BaseACtor -> Actor 변환시 Actor.moves 등록시 사용
     */
    public List<Long> getDefaultMoveIds() {
        List<Long> ids = new ArrayList<>();
        if (this.mappedMove != null) {
            if (this.mappedMove.getNormalAttackId() != null) {
                ids.add(this.mappedMove.getNormalAttackId());
            }
            if (this.mappedMove.getStandbyId() != null) {
                ids.add(this.mappedMove.getStandbyId());
            }
            ids.addAll(this.mappedMove.getAbilityIds());
            ids.addAll(this.mappedMove.getSupportAbilityIds());
            ids.addAll(this.mappedMove.getChargeAttackIds());
            ids.addAll(this.mappedMove.getChangingMoveIds());
        }
        return ids;
    }

    /**
     * 등록된 모든 moveId 를 반환. <br>
     * 캐릭터의 메타데이터 확인시, 특히 주인공은 모든 어빌리티/서포트 어빌리티 확인 필요
     */
    public List<Long> getAllMoveIds() {
        Set<Long> allMoveIds = new LinkedHashSet<>(this.getDefaultMoveIds());  // 순서 유지
        if (this.mappedMove != null) {
            allMoveIds.addAll(this.mappedMove.getAllAbilityIds());
            allMoveIds.addAll(this.mappedMove.getAllSupportAbilityIds());
        }
        return new ArrayList<>(allMoveIds);
    }


}
