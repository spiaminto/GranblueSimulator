package com.gbf.granblue_simulator.domain.actor;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode(callSuper = false) @ToString(callSuper = true)
@Inheritance(strategy = InheritanceType.JOINED)
public class Enemy extends Actor{

    @Type(ListArrayType.class)
    @Column(name = "hp_triggers", columnDefinition = "integer[]")
    private List<Integer> hpTriggers = new ArrayList<>();

    // 브금용
    @Type(ListArrayType.class)
    @Column(name = "bgm_triggers", columnDefinition = "integer[]")
    private List<Integer> bgmTriggers = new ArrayList<>();

    @Type(ListArrayType.class)
    @Column(name = "bgm_srcs", columnDefinition = "text[]")
    private List<String> bgmSrcs= new ArrayList<>();

    // 배경, jpg
    private String backgroundImageSrc;
}
