package com.gbf.granblue_simulator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class Room {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String info; // 방 정보 (밖에 표시할 이름)

    @Builder.Default
    private Integer maxUserCount = 3; // 최대 유저 수

    @OneToMany(mappedBy = "room")
    private List<Member> members; // 방에 있는 유저들

    private Long enemyActorId; // 편의용 적 id
}
