package com.gbf.granblue_simulator.metadata.domain.actor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import jakarta.persistence.Transient;
import lombok.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@ToString
@EqualsAndHashCode
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MappedMove {

    // 기본 사용 매핑
    private Long normalAttackId;
    @Builder.Default
    private List<Long> chargeAttackIds = new ArrayList<>(); // 순서대로 (a, b, c...)
    @Builder.Default
    private List<Long> abilityIds = new ArrayList<>(); // 순서대로
    @Builder.Default
    private List<Long> supportAbilityIds = new ArrayList<>(); // 순서대로
    @Builder.Default
    private List<Long> triggeredAbilityIds = new ArrayList<>(); // 순서대로, parentType 이 SUPPORT_ABILITY 임.
    private Long standbyId;

    // 가변
    @Builder.Default
    private List<Long> changingMoveIds = new ArrayList<>(); // 순서 x

    // 모든 어빌리티 (주인공)
    @Builder.Default
    private List<Long> allAbilityIds = new ArrayList<>(); // 순서대로
    @Builder.Default
    private List<Long> allSupportAbilityIds = new ArrayList<>(); // 순서대로

    @Transient
    @JsonIgnore
    @Builder.Default
    private Map<Long, MoveType> moveTypeById = new HashMap<>();

    public void postLoad() {
        this.moveTypeById = this.groupMoveTypeById(this); // triggeredAbilityIds, changingMoveIds 는 Move 초기화시 사용하지 않으므로 캐싱안함

//         moveIdByType.forEach((k, v) -> System.out.println(k + " : " + v.name()));
    }


    public Map<Long, MoveType> groupMoveTypeById(MappedMove mappedMove) {
        Long normalAttackId = mappedMove.getNormalAttackId();
        Long standbyId = mappedMove.getStandbyId();
        List<Long> chargeAttackIds = mappedMove.getChargeAttackIds();
        List<Long> abilityIds = mappedMove.getAbilityIds();
        List<Long> supportAbilityIds = mappedMove.getSupportAbilityIds();

        Map<Long, MoveType> moveIdByType = new HashMap<>();
        if (normalAttackId != null)
            moveIdByType.put(this.normalAttackId, MoveType.NORMAL_ATTACK);
        if (standbyId != null)
            moveIdByType.put(standbyId, MoveType.STANDBY);

        if (chargeAttackIds.size() > 1) {
            MoveType.CHARGE_ATTACKS.forEach(chargeAttackType -> {
                int index = chargeAttackType.getOrder() - 1;
                if (chargeAttackIds.size() <= index) return;
                moveIdByType.put(chargeAttackIds.get(index), chargeAttackType);
            });
        } else if (chargeAttackIds.size() == 1) {
            moveIdByType.put(chargeAttackIds.getFirst(), MoveType.CHARGE_ATTACK_DEFAULT);
        }

        MoveType.ABILITIES.forEach(abilityType -> {
            int index = abilityType.getOrder() - 1;
            if (abilityIds.size() <= index) return;
            moveIdByType.put(abilityIds.get(index), abilityType);
        });
        MoveType.SUPPORT_ABILITIES.forEach(abilityType -> {
            int index = abilityType.getOrder() - 1;
            if (supportAbilityIds.size() <= index) return;
            moveIdByType.put(supportAbilityIds.get(index), abilityType);
        });

        return moveIdByType;
    }

}

/*
{
    "chargeAttackIds": [1143],
    "abilityIds": [1145, 1146, 1148],
    "supportAbilityIds": [1149, 1150, 1151, 1152],
    "standbyId": null,
    "changingMoveIds": []
}
 */