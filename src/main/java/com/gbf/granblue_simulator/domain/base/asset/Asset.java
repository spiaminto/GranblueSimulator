package com.gbf.granblue_simulator.domain.base.asset;

import jakarta.persistence.*;
import lombok.*;

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

    private String cjsName;

    private String rootName; // type ACTOR 기준이름. 사용가능한 Asset 을 필터링 하기 위함 (하이라 -> 하이라(기본), 하이라(수영복) or 주인공 -> 팔라딘(기본), 실드스완(기본) 등)
    private String name; // 이름

    private String portraitImageSrc; // ACTOR: battlePortrait

    private Long actorId;
    private Long moveId;

    private int chargeAttackStartFrame; // 오의 스킵시 시작프레임
    private int hitStartFrame; // 실제 hit 하는 프레임
    private boolean isTargetedEnemy; // targeted 어빌리티의 이펙트 target

}
