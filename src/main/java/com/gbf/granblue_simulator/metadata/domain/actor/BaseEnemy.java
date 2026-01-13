package com.gbf.granblue_simulator.metadata.domain.actor;

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
@Getter @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Inheritance(strategy = InheritanceType.JOINED)
public class BaseEnemy extends BaseActor {

    private String rootNameEn; // 적의 경우 폼 구별을 위한 별도 필드, 일단 id 말고 이렇게 사용
    private Integer formOrder; // 폼, 1부터 시작

}
