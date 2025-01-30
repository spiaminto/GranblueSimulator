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
    
    private String infoText; // 방밖에 표시할 이름

    private Integer maxUserCount; // 최대 유저 수

    @OneToMany(mappedBy = "room")
    private List<Member> members; // 방에 있는 유저들
}
