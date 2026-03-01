package com.gbf.granblue_simulator.user.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.actor.MappedMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
public class UserCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_character_seq_gen")
    @SequenceGenerator(
            name = "user_character_seq_gen",
            sequenceName = "user_character_seq",
            allocationSize = 50)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_visual_id")
    private ActorVisual customVisual;

    // abilities { baseMoveId { moveType: MOVETYPE, status: STATUS} }
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "abilities", columnDefinition = "json") // 순서 유지를 위해 jsonb 대신 json
    private Map<Long, UserCharacterMove> abilities = new LinkedHashMap<>(); // TreeMap 은 키 순서를 보장한다고 함.

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @OneToOne
    @JoinColumn(name = "base_character_id")
    private BaseCharacter baseCharacter;

    @Transient
    @JsonIgnore
    private MappedMove customMappedMove; // 주인공 등

    @Transient
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    private Map<Long, MoveType> moveTypeById;

    public boolean isLeaderCharacter() {
        return this.baseCharacter.isLeaderCharacter();
    }

    /**
     * 전투를 위한 Actor 생성시 사용할 moveIds 를 반환 <br>
     * 일반 캐릭터는 기본 move.id, 주인공 캐릭터는 전체 어빌리티 중 사용중이 아닌 어빌리티를 제외한 move.id 반환
     */
    public List<Long> getBattleMoveIds() {
        if (!this.isLeaderCharacter()) return this.baseCharacter.getDefaultMoveIds();

        return this.baseCharacter.getAllMoveIds().stream()
                .filter(moveId -> {
                    UserCharacterMove userCharacterMove = this.abilities.get(moveId);
                    return userCharacterMove == null || userCharacterMove.getStatus() == UserCharacterMoveStatus.IN_USE;
                })
                .toList();
    }

    /**
     * 주인공은 어빌리티 상태 여부에 따라 런타임용 Move.moveType 이 변경되므로 어빌리티 상태를 반영한 타입정보 반환
     */
    public Map<Long, MoveType> getMoveTypeById() {
        if (this.moveTypeById == null) this.setCustomMappedMove();
        return this.moveTypeById;
    }

    private void setCustomMappedMove() {
        MappedMove defaultMappedMove = this.getBaseCharacter().getMappedMove();
        MappedMove customMappedMove = MappedMove.builder()
                .chargeAttackIds(defaultMappedMove.getChargeAttackIds())
                .normalAttackId(defaultMappedMove.getNormalAttackId())
                .abilityIds(this.abilities.entrySet().stream()
                        .filter(entry -> entry.getValue().getMoveType() == MoveType.ABILITY && entry.getValue().getStatus() == UserCharacterMoveStatus.IN_USE)
                        .map(Map.Entry::getKey).toList())
                .supportAbilityIds(this.abilities.entrySet().stream()
                        .filter(entry -> entry.getValue().getMoveType() == MoveType.SUPPORT_ABILITY && entry.getValue().getStatus() == UserCharacterMoveStatus.IN_USE)
                        .map(Map.Entry::getKey).toList())
                .build();
        this.customMappedMove = customMappedMove;
        this.moveTypeById = customMappedMove.groupMoveTypeById(customMappedMove);
    }
}
