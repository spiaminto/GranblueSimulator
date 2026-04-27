package com.gbf.granblue_simulator.party.domain;

import com.gbf.granblue_simulator.user.domain.User;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Type(ListArrayType.class)
    @Column(name = "user_character_ids", columnDefinition = "bigint[]")
    @Builder.Default
    private List<Long> userCharacterIds = new ArrayList<>();

    @Type(ListArrayType.class)
    @Column(name = "summon_ids", columnDefinition = "bigint[]")
    @Builder.Default
    private List<Long> summonIds = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_party_id")
    @ToString.Exclude
    private BaseParty baseParty;

}
