package com.gbf.granblue_simulator.domain.move.prop;

import com.gbf.granblue_simulator.domain.move.prop.sub.Audio;
import com.gbf.granblue_simulator.domain.move.prop.sub.Image;
import com.gbf.granblue_simulator.domain.move.prop.sub.Video;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString
public class Asset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private Image image;

    @OneToMany(mappedBy = "asset")
    private List<Audio> audios;

    @OneToOne
    private Video video;
}
