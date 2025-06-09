package com.gbf.granblue_simulator.domain.move.prop.asset;

import com.gbf.granblue_simulator.domain.move.Move;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString(exclude = {"move"})
public class Asset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne @JoinColumn(name = "move_id")
    private Move move;

    private String seAudioSrc;
    private String voiceAudioSrc;

    private String effectVideoSrc;
    @Builder.Default
    private Integer effectHitDelay = 0; // 이펙트 ~ 히트 까지 간격(ms). 이 딜레이 이후로 피격이펙트 표시 (일부 적의 오의)
    private String motionVideoSrc;
    @Builder.Default
    private boolean motionVideoFull = false;

    private String iconImageSrc; // 어빌리티 아이콘, 소환석 포트레잇 및 기타


    public void setMove(Move move) {
        this.move = move;
    }
}
