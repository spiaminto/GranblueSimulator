package com.gbf.granblue_simulator.domain;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 파티 이름
    private String infoText; // 파티 설명

    @Type(ListArrayType.class) @Column(name = "actor_ids", columnDefinition = "bigint[]") @Builder.Default
    private List<Long> actorIds = new ArrayList<>();

    @Type(ListArrayType.class) @Column(name = "actor_asset_ids", columnDefinition = "bigint[]") @Builder.Default
    private List<Long> actorAssetIds = new ArrayList<>(); // 에셋변경시 여기에 저장

    @Type(ListArrayType.class) @Column(name = "summon_ids", columnDefinition = "bigint[]") @Builder.Default
    private List<Long> summonIds = new ArrayList<>();

    // 나중에 주인공 스킬 가변으로 변경시 List<Long> mainCharacterAbilityIds 등의 필드 추가

}
