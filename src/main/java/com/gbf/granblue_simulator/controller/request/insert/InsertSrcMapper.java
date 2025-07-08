package com.gbf.granblue_simulator.controller.request.insert;

import com.gbf.granblue_simulator.domain.move.MoveType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.IntStream;

@Slf4j
public final class InsertSrcMapper {

    private static final String CHARACTER_IMG_BASE_URL = "/static/assets/img/ch/";
    private static final String CHARACTER_VIDEO_BASE_URL = "/static/assets/video/ch/";
    private static final String CHARACTER_AUDIO_BASE_URL = "/static/assets/audio/ch/";

    private static final String SUMMON_IMG_BASE_URL = "/static/assets/img/ch/";
    private static final String SUMMON_VIDEO_BASE_URL = "/static/assets/video/ch/";
    private static final String SUMMON_AUDIO_BASE_URL = "/static/assets/audio/ch/";


    private static final String WEBM = ".webm";
    private static final String JPG = ".jpg";
    private static final String PNG = ".png";
    private static final String MP3 = ".mp3";

    private static final String VOICE = "-v"; // EFFECT는 없음
    private static final String MOTION = "-m"; // EFFECT는 없음

    private static String getFileName(MoveType moveType) {
        return switch (moveType) {
            case IDLE_DEFAULT -> "idle";
            case DAMAGED_DEFAULT -> "damaged";
            case CHARGE_ATTACK_DEFAULT -> "ca";
            case SUMMON_DEFAULT -> "summon";
            case SINGLE_ATTACK -> "sa";
            case DOUBLE_ATTACK -> "da";
            case TRIPLE_ATTACK -> "ta";
            case FIRST_ABILITY -> "ab-1";
            case SECOND_ABILITY -> "ab-2";
            case THIRD_ABILITY -> "ab-3";
            case FOURTH_ABILITY -> "ab-4";
            case FIRST_SUPPORT_ABILITY -> "as-1";
            case SECOND_SUPPORT_ABILITY -> "as-2";
            case THIRD_SUPPORT_ABILITY -> "as-3";
            case FOURTH_SUPPORT_ABILITY -> "as-4";
            case FIFTH_SUPPORT_ABILITY -> "as-5";
            case SIXTH_SUPPORT_ABILITY -> "as-6";
            case SEVENTH_SUPPORT_ABILITY -> "as-7";
            case EIGHTH_SUPPORT_ABILITY -> "as-8";
            case NINTH_SUPPORT_ABILITY -> "as-9";
            case TENTH_SUPPORT_ABILITY -> "as-10";
            default -> throw new IllegalArgumentException("[getFileName] switch default moveType = " + moveType);
        };
    }

    /**
     * /name/battle-portrait.jpg
     *
     * @param nameEn
     * @return
     */
    public static String getBattlePortraitSrc(String nameEn) {
        return CHARACTER_IMG_BASE_URL + nameEn + "/battle-portrait" + JPG;
    }

    public static String getSummonPortraitSrc(String nameEn) {
        return SUMMON_IMG_BASE_URL + nameEn + "/battle-portrait" + JPG;
    }

    /**
     * /name/ab-1.webm
     *
     * @param nameEn
     * @param moveType
     * @return
     */
    public static String getEffectVideoSrc(String nameEn, MoveType moveType) {
        if (moveType == MoveType.SUMMON_DEFAULT) {
            return SUMMON_VIDEO_BASE_URL + nameEn + "/" + getFileName(moveType) + WEBM;
        } else { // 일반
            return CHARACTER_VIDEO_BASE_URL + nameEn + "/" + getFileName(moveType) + WEBM;
        }
    }

    /**
     * /name/ab-1-m.webm
     *
     * @param nameEn
     * @param moveType
     * @return
     */
    public static String getMotionVideoSrc(String nameEn, MoveType moveType) {
        return CHARACTER_VIDEO_BASE_URL + nameEn + "/" + getFileName(moveType) + MOTION + WEBM;
    }

    /**
     * /name/ab-1.mp3
     *
     * @param nameEn
     * @param moveType
     * @return
     */
    public static String getSeAudioSrc(String nameEn, MoveType moveType) {
        if (moveType == MoveType.SUMMON_DEFAULT) {
            return SUMMON_AUDIO_BASE_URL + nameEn + "/" + getFileName(moveType) + MP3;
        } else {
            return CHARACTER_AUDIO_BASE_URL + nameEn + "/" + getFileName(moveType) + MP3;
        }
    }

    /**
     * /name/ab-1-v.mp3
     *
     * @param nameEn
     * @param moveType
     * @return
     */
    public static String getVoiceAudioSrc(String nameEn, MoveType moveType) {
        return CHARACTER_AUDIO_BASE_URL + nameEn + "/" + getFileName(moveType) + VOICE + MP3;
    }

    public static String getAbilityIconSrc(String nameEn, MoveType moveType) {
        return CHARACTER_IMG_BASE_URL + nameEn + "/" + getFileName(moveType) + PNG;
    }

    /**
     * /name/ab-1-s1.png
     * /name/ab-1-s1-1.png (레벨제)
     * ( /{name}/{moveTypeFileName}-s{statusOrder}-{level} )
     *
     * @param nameEn
     * @param moveType
     * @param statusOrder
     * @param maxLevel
     * @return
     */
    public static List<String> getStatusIconSrcs(String nameEn, MoveType moveType, Integer statusOrder, Integer maxLevel) {
        return maxLevel == 0 ?
                List.of(CHARACTER_IMG_BASE_URL + nameEn + "/" + getFileName(moveType) + "-s" + statusOrder + PNG) : // 레벨 X
                IntStream.range(1, maxLevel + 1)
                        .mapToObj(level -> CHARACTER_IMG_BASE_URL + nameEn + "/" + getFileName(moveType) + "-s" + statusOrder + "-" + level + PNG)
                        .toList(); // 레벨제
    }

    public static List<String> getSummonStatusIconSrcs(String nameEn, Integer statusOrder, Integer maxLevel) {
        return maxLevel == 0 ?
                List.of(SUMMON_IMG_BASE_URL + nameEn + "/s" + statusOrder + PNG) :
                IntStream.range(1, maxLevel + 1)
                        .mapToObj(level -> SUMMON_IMG_BASE_URL + nameEn + "/s" + statusOrder + "-" + level + PNG)
                        .toList(); // 레벨제
    }

}
