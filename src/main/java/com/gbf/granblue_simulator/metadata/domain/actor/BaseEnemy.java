package com.gbf.granblue_simulator.metadata.domain.actor;

import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
public class BaseEnemy extends BaseActor {

    private String rootNameEn; // 적의 경우 폼 구별을 위한 별도 필드, 일단 id 말고 이렇게 사용
    private Integer formOrder; // 폼, 1부터 시작

    @OneToMany(mappedBy = "enemy") @MapKey(name = "standbyType") @ToString.Exclude @EqualsAndHashCode.Exclude
    private Map<MoveType, BaseOmen> omens = new HashMap<>();

}
