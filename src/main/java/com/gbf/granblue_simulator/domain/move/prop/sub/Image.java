package com.gbf.granblue_simulator.domain.move.prop.sub;

import com.gbf.granblue_simulator.domain.move.prop.Asset;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class Image {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    private Asset asset;

    private String src; // 이미지 요청 경로


}
