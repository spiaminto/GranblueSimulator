package com.gbf.granblue_simulator.domain.move.prop;

import com.gbf.granblue_simulator.domain.move.prop.sub.Image;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString
public class Status {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private StatusType type;

    @Enumerated(EnumType.STRING)
    private StatusSubType subType;
    
    private String effectText; // 이펙트에 띄울 텍스트
    private String statusText; // 스테이터스 창에 띄울 텍스트

    @OneToOne
    private Image image;
}