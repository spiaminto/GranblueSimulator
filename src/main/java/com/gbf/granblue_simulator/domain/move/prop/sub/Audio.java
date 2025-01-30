package com.gbf.granblue_simulator.domain.move.prop.sub;

import com.gbf.granblue_simulator.domain.move.prop.Asset;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class Audio {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "asset_id")
    private Asset asset;

    private String src; // 오디오 요청 경로

    private Integer delay; // 오디오 재생 지연 시간 (ms)
}
