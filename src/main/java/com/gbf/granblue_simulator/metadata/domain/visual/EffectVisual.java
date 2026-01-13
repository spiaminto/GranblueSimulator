package com.gbf.granblue_simulator.metadata.domain.visual;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString
public class EffectVisual { // attack, ability, chargeattack

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private EffectVisualType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_visual_id")
    private ActorVisual actorVisual;

    private String cjsName; // effectCjs

    private int chargeAttackStartFrame; // 오의 스킵시 시작프레임
    private boolean isTargetedEnemy; // targeted 어빌리티의 이펙트 target

    public String getPortraitImageSrc() {
        // 일단 소환석만 사용중
        String gid = this.cjsName.replace("summon_", "");
        return "/static/gbf/img/bp/" + gid + ".jpg";
    }

}
