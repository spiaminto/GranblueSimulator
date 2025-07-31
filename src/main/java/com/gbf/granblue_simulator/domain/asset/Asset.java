package com.gbf.granblue_simulator.domain.asset;

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
@EqualsAndHashCode @ToString
public class Asset {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AssetType type;

    private String rootCjsName; // type == ACTOR 의 name, Actor 의 하위 모든 asset 을 끌어오기 위함
    @Type(ListArrayType.class) @Column(name = "cjs_names", columnDefinition = "text[]") @Builder.Default
    private List<String> cjsNames = new ArrayList<>(); // cjs(manifest) name, sprite 는 파일만 있으면 자동로드
    // mainCjs : 1개, (additional 존재시 2개)
    // abilityCjs : n개
    // specialCjs : n개 (additional 존재시 n + m 개)
    // phitCjs : 1 개

    private String cjsName;

    private String rootName; // type ACTOR 기준이름. 사용가능한 Asset 을 필터링 하기 위함 (하이라 -> 하이라(기본), 하이라(수영복) or 주인공 -> 팔라딘(기본), 실드스완(기본) 등)
    private String name; // 이름

    private String portraitImageSrc; // ACTOR: battlePortrait

    private Long actorId;
    private Long moveId;

    private int chargeAttackStartFrame; // 오의 스킵시 시작프레임
    private int chargeAttackSkipStartFrame; // 오의 스킵시 시작 프레임 (예비, 최소시간)
    private int hitStartFrame; // 실제 hit 하는 프레임
    private boolean isTargetedEnemy; // targeted 어빌리티의 이펙트 target

//    @Builder.Default // CHECK 얘는 MOVE 로 옮겨야 될듯.
//    private Integer effectHitDelay = 0; // 이펙트 ~ 히트 까지 간격(ms). 이 딜레이 이후로 피격이펙트 표시 (일부 적의 오의)

}
