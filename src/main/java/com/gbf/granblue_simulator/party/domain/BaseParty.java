package com.gbf.granblue_simulator.party.domain;

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
public class BaseParty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 파티 이름
    private String info; // 파티 설명

    @Enumerated(EnumType.STRING)
    private PartyStatus status;

    @Type(ListArrayType.class)
    @Column(name = "base_character_ids", columnDefinition = "bigint[]")
    @Builder.Default
    private List<Long> baseCharacterIds = new ArrayList<>();

    @Type(ListArrayType.class)
    @Column(name = "summon_ids", columnDefinition = "bigint[]")
    @Builder.Default
    private List<Long> summonIds = new ArrayList<>();

}
