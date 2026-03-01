package com.gbf.granblue_simulator.battle.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private RoomStatus roomStatus;
    private String info; // 방 정보 (밖에 표시할 이름)

    private Long ownerId; // 방장 userid
    private String ownerUsername; // 방장 유저네임

    @Builder.Default
    private Integer maxUserCount = 3; // 최대 유저 수
    private int enterUserCount = 0; // 입장한 유저수

    @OneToMany(mappedBy = "room")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Member> members = new ArrayList<>(); // 방에 있는 유저들

    private Long enemyBaseId; // 편의용 적 id

    private Long unionSummonId; // 대기중인 합체소환 id (Move.id)

    private boolean isHidden;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime endedAt; // 종료시각 (종료 여부 판별도 이걸로)

    public void updateEnterUserCount(int count) {
        this.enterUserCount = count;
    }

    public void updateIsHidden(boolean isHidden) {
        this.isHidden = isHidden;
    }

    public void updateInfo(String info) {
        this.info = info;
    }

    /**
     * 방 상태 변경
     */
    public void changeStatus(RoomStatus roomStatus) {
        this.roomStatus = roomStatus;
        if (roomStatus == RoomStatus.CLEARED || roomStatus == RoomStatus.FAILED_TIMEOUT || roomStatus == RoomStatus.FAILED_EMPTY) {
            this.endedAt = LocalDateTime.now();
        }
    }

    public void updateEndedAtNow() {
        this.endedAt = LocalDateTime.now();
    }


    public void setOwner(Long ownerId, String ownerUsername) {
        this.ownerId = ownerId;
        this.ownerUsername = ownerUsername;
    }

    public boolean isFinished() {
        return this.roomStatus == RoomStatus.CLEARED
                || this.roomStatus == RoomStatus.FAILED_TIMEOUT
                || this.roomStatus == RoomStatus.FAILED_EMPTY;
    }

    /**
     * 합체소환 id 세팅 <br>
     *
     * @param unionSummonId 초기화는 null
     */
    public void updateUnionSummonId(Long unionSummonId) {
        this.unionSummonId = unionSummonId;
    }

}
