package com.gbf.granblue_simulator.battle.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class RoomChat {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long roomId;
    private Long userId;

    private String username;

    @Enumerated(EnumType.STRING)
    private ChatType type; // TEXT | STAMP

    private String content;
    private ChatStamp stamp;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
