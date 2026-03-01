package com.gbf.granblue_simulator.user.domain;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.party.domain.Party;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String loginId;
    private String username; // 유저 이름 (8자 제한)
    private String password; // 유저 비밀번호 (20자 제한)

    private String role;

    @OneToMany(mappedBy = "user") @Builder.Default @ToString.Exclude @EqualsAndHashCode.Exclude
    private List<Member> members = new ArrayList<>(); // 양방향 나중에 지울 예정

    @OneToMany(mappedBy = "user") @Builder.Default @ToString.Exclude @EqualsAndHashCode.Exclude
    private List<Party> parties = new ArrayList<>();

    @OneToMany(mappedBy = "user") @MapKey(name = "id") @Builder.Default @ToString.Exclude @EqualsAndHashCode.Exclude
    private Map<Long, UserCharacter> userCharacters = new LinkedHashMap<>();

    private Long primaryPartyId; // 현재 선택중인 파티 id

    @CreationTimestamp
    private LocalDateTime lastChatTime; // 마지막 채팅 시간

    public void updatePrimaryPartyId(Long primaryPartyId) {
        this.primaryPartyId = primaryPartyId;
    }

    public void updateLastChatTime(LocalDateTime lastChatTime) {
        this.lastChatTime = lastChatTime;
    }

}
