package com.gbf.granblue_simulator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private Long ownerId; // 방장 userid
    private String ownerUsername; // 방장 유저네임

    @Builder.Default
    private Integer maxUserCount = 3; // 최대 유저 수

    @OneToMany(mappedBy = "room") @Builder.Default
    private List<Member> members = new ArrayList<>(); // 방에 있는 유저들

    private Long enemyActorId; // 편의용 적 id

    public void setOwner(Long ownerId, String ownerUsername) {
        this.ownerId = ownerId;
        this.ownerUsername = ownerUsername;
    }

    public void setEnemyActorId() {
        this.enemyActorId = 7L; // 현재 디아스포라(7) 로 고정
    }
}
