package com.gbf.granblue_simulator.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username; // 유저 이름
    private String password; // 유저 비밀번호

    private String role;

    @OneToMany(mappedBy = "user") @Builder.Default @ToString.Exclude @EqualsAndHashCode.Exclude
    private List<Member> members = new ArrayList<>(); // 이거 나중에 지울 예정

    private Long primaryPartyId; // 현재 선택중인 파티 id

    public void setPrimaryParty(Long primaryPartyId) {
        this.primaryPartyId = primaryPartyId;
    }

}
