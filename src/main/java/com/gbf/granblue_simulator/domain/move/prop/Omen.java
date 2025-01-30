package com.gbf.granblue_simulator.domain.move.prop;

import jakarta.persistence.*;
import lombok.*;

// 전조
@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class Omen {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 차지어택 이름 따라갈 예정

    @Enumerated(EnumType.STRING)
    private OmenType omenType;

    private String statusText; // 스테이터스 창에 띄울 텍스트

    private String disableCondition; // 해제조건 (보스 체력 아래에 표시될 텍스트)
    
    private Integer value; // 해제 조건값

}
