package com.gbf.granblue_simulator.metadata.domain.visual;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@ToString @EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ActorVisual {

    @Id @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // BaseActor.defaultVisual 에 set 만 해둠. 런타임때 조정

    private String name; // 한글이름, actor.name 따라갈 예정

    @OneToMany(mappedBy = "actorVisual")
    @ToString.Exclude
    private List<EffectVisual> effectVisuals = new ArrayList<>();

    @Transient @ToString.Exclude
    private List<EffectVisual> attackVisuals = new ArrayList<>();
    @Transient @ToString.Exclude
    private List<EffectVisual> chargeAttackVisuals = new ArrayList<>();
    @Transient @ToString.Exclude
    private List<EffectVisual> additionalChargeAttackVisuals = new ArrayList<>();

    private String cjsName; // mainCjs
    private String additionalCjsName; // additionalCjsName (주로 보스용)

    private String weaponId;
    private String gid; // 참조용 gbf id

    @PostLoad
    protected void initEffectVisuals() { // 맵 도 고려
        Map<EffectVisualType, List<EffectVisual>> map = effectVisuals.stream()
                .collect(Collectors.groupingBy(EffectVisual::getType));

        this.attackVisuals = map.getOrDefault(EffectVisualType.ATTACK, new ArrayList<>());
        this.chargeAttackVisuals = map.getOrDefault(EffectVisualType.CHARGE_ATTACK, new ArrayList<>());
        this.additionalChargeAttackVisuals = map.getOrDefault(EffectVisualType.ADDITIONAL_CHARGE_ATTACK, new ArrayList<>());
    }

    public String getPortraitImageSrc() {
        return "/static/gbf/img/bp/" + gid + ".jpg";
    }

    public String getBodyImageSrc() {
        return "/static/gbf/img/body/" + gid + ".png"; // 어따쓸진 아직 잘 모르겟음
    }

}
