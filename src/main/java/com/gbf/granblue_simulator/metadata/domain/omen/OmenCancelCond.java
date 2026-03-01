package com.gbf.granblue_simulator.metadata.domain.omen;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString(exclude = "omen")
public class OmenCancelCond {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private OmenCancelType type; // 해제조건 타입

    private String info; // 해제조건표시용 (보스 체력 아래에 표시될 텍스트)
    private Integer initValue; // 해제 조건 초기값

    @ManyToOne @JoinColumn(name = "omen_id")
    private BaseOmen omen;

    public void setOmen(BaseOmen omen) {
        this.omen = omen;
        omen.getOmenCancelConds().add(this);
    }


}
