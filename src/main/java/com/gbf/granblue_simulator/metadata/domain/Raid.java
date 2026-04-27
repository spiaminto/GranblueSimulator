package com.gbf.granblue_simulator.metadata.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
public class Raid {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private RaidType type;

    private String name;
    private String info;
    private String raidImageSrc;

    private Long firstBaseEnemyId;
    private String baseEnemyRootNameEn;
}
