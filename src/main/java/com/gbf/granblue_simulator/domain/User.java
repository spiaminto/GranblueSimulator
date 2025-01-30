package com.gbf.granblue_simulator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 유저 이름
    private String password; // 유저 비밀번호

    @OneToMany(mappedBy = "user")
    private List<Member> members; // 이거 나중에 지울 예정

}
