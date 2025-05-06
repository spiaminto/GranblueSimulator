package com.gbf.granblue_simulator.domain.move.prop.asset;

import com.gbf.granblue_simulator.domain.actor.Character;
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
    private String motionVideoSrc;

    private String iconImageSrc; // 어빌리티 아이콘, 소환석 포트레잇 및 기타


    public void setMove(Move move) {
        this.move = move;
    }
}
