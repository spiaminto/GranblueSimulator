package com.gbf.granblue_simulator.domain.actor;

import com.gbf.granblue_simulator.domain.Member;
import jakarta.persistence.*;
import lombok.*;

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

    // 여기는 character 가 의존하도록 하면 파티마다 전부 character 를 가지게 되므로 그냥 의존관계 없이 id 만 저장하고 불러오기
    // 딱히 파티를 자주 불러올것 같지도않고, 전투중에도 파티가 아니라 userId 에 의존하는 BattleCharacter 를 사용하므로
    private Long firstActorId; // 첫번째 캐릭터
    private Long secondActorId; // 두번째 캐릭터
    private Long thirdActorId; // 세번째 캐릭터
    private Long fourthActorId; // 네번째 캐릭터

    // 소환석 id (변경 가능하니 리스트가 아닌 id 전부 가지도록 함 일단)
    private Long firstSummonMoveId;
    private Long secondSummonMoveId;
    private Long thirdSummonMoveId;
    private Long fourthSummonMoveId;

    public List<Long> getActorIds() {
        return List.of(this.firstActorId, this.secondActorId, this.thirdActorId, this.fourthActorId);
    }

    public List<Long> getSummonMoveIds() {
        return List.of(this.firstSummonMoveId, this.secondSummonMoveId, this.thirdSummonMoveId, this.fourthSummonMoveId);
    }
}
