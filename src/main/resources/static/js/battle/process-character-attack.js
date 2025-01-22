
/**
 *  아군 캐릭터 하나의 공격행동 실행
 * @param order 캐릭터의 순서
 * @param hitCount 캐릭터의 평타 타수
 * @param additionalHitCount 캐릭터의 추격갯수
 * @param audioPlayers 오디오 플레이어 맵, keys = 1, 2, 3, enemy, global
 */
function processCharacterAttack(order, hitCount, additionalHitCount, audioPlayers) {

    console.log('turn start ' + order)

    // 다음 캐릭터 공격 오디오 미리로드 async
    let nextOrder = order + 1;
    let nextCharacterAttackAudioSrc = $('.party-' + nextOrder + '-audio').attr('src');
    console.log(''+nextCharacterAttackAudioSrc)
    if (nextCharacterAttackAudioSrc != null) {
        audioPlayers.get(nextOrder).loadSound(nextCharacterAttackAudioSrc).then(() => {
            console.log('nextCharacterAudioLoaded');
        })
    }

    // 데미지 계산 및 채우기 선행
    // ...

    // 변수초기화
    let partySelector = '.party-' + order
    // let attackMotionDelays = [0, 900, 1150, 1550];
    let attackMotionDelays = [0, 700, 950, 1350];

    // 아군 공격모션
    let $attackMotionVideo = $(partySelector + '.motion-attack-' + hitCount);
    setTimeout(function () {
        $(partySelector + '.motion-idle').addClass('hidden').get(0).pause();
        $attackMotionVideo.removeClass('hidden').get(0).play();
    }, 300);
    $attackMotionVideo.one('ended', function () {
        $(this).addClass('hidden');
        $(partySelector + '.motion-idle').removeClass('hidden').get(0).play();

        $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
    })

    // 반복 플레이
    let attackSePlayIntervalIndex = 0;
    let attackSePlayInterval = setInterval(async function () {

        playAttackSe(attackSePlayIntervalIndex);
        playEnemyDamagedMotion();
        showDamage(attackSePlayIntervalIndex + 1); // selecter 1부터

        // 인터벌 끝
        if (++attackSePlayIntervalIndex >= hitCount) {
            clearInterval(attackSePlayInterval);
        }
    }, attackMotionDelays[hitCount] / (hitCount));

    function playAttackSe() {
        audioPlayers.get(order).playAllSounds();
        return true;
    }

    function playEnemyDamagedMotion() {
        $('.motion-enemy-idle').addClass('hidden');
        let motionDamagedElement = $('.motion-enemy-damaged').removeClass('hidden').get(0);
        motionDamagedElement.play();
        $(motionDamagedElement).one('ended', function () {
            $(this).addClass('hidden')
            $('.motion-enemy-idle').removeClass('hidden');
        })
        return true;
    }

    function showDamage(damageOrder) {
        // 데미지 표시
        $('.damage-attack-' + damageOrder + ' .damage-1').fadeIn('fast').delay(300).fadeOut();
        if (additionalHitCount > 0) {
            $('.damage-attack-' + damageOrder + ' .damage-2').fadeIn('fast').delay(300).fadeOut();
            if (additionalHitCount > 1) {
                $('.damage-attack-' + damageOrder + ' .damage-3').fadeIn('fast').delay(300).fadeOut();
            }
        }
        return true;
    }


    //종료
}// processParty


/* 레거시 */
/**
 *  아군 캐릭터 하나의 공격행동 실행
 * @param order 캐릭터의 순서
 * @param hitCount 캐릭터의 평타 타수
 * @param additionalHitCount 캐릭터의 추격갯수
 */
function processCharacterAttackLegacy(order, hitCount, additionalHitCount) {

    // 데미지 계산 및 채우기 선행

    let partySelector = '.party-' + order
    let firstAttackDamageDisplayDelay = hitCount === 1 ? 300 : 400

    // 1타
    // 아군 공격 모션 재생
    $(partySelector + '.motion-idle').addClass('hidden').get(0).pause();

    // 아군 공격모션
    if (hitCount === 1) {
        $(partySelector + '.motion-single-attack-full').removeClass('hidden').get(0).play();
    } else {
        $(partySelector + '.motion-single-attack-short').removeClass('hidden').get(0).play();
    }

    // 적 피격모션
    $('.motion-enemy-idle').addClass('hidden');
    let motionDamagedElement = $('.motion-enemy-damaged').removeClass('hidden').get(0);
    motionDamagedElement.currentTime = 0;
    motionDamagedElement.play();

    // 효과음 재생
    let audioElement = $(partySelector + '-audio').get(0);
    audioElement.currentTime = 0;
    audioElement.play();

    // 데미지 표시
    $('.damage-first .origin-damage').fadeIn().delay(firstAttackDamageDisplayDelay).fadeOut();
    if (additionalHitCount > 0) {
        $('.damage-first .damage-2').fadeIn().delay(firstAttackDamageDisplayDelay).fadeOut();
        if (additionalHitCount > 1) {
            $('.damage-first .damage-3').fadeIn().delay(firstAttackDamageDisplayDelay).fadeOut();
        }
    }

    // 2타
    $(partySelector + '.motion-single-attack').one('ended', function () {
        console.log('double fire')

        if (hitCount >= 2) {
            // 아군 공격모션
            $(partySelector + '.motion-single-attack').addClass('hidden');
            $(partySelector + '.motion-double-attack').removeClass('hidden').get(0).play();

            // 적 피격모션
            $('.motion-enemy-idle').addClass('hidden');
            let motionDamagedElement = $('.motion-enemy-damaged').removeClass('hidden').get(0);
            motionDamagedElement.currentTime = 0;
            motionDamagedElement.play();

            // 효과음 재생
            let audioElement = $(partySelector + '-audio').get(0);
            audioElement.currentTime = 0;
            audioElement.play();

            // 데미지 표시
            $('.damage-second .origin-damage').fadeIn().delay('400').fadeOut();
            if (additionalHitCount > 0) {
                $('.damage-second .damage-2').fadeIn().delay('400').fadeOut();
                if (additionalHitCount > 1) {
                    $('.damage-second .damage-3').fadeIn().delay('400').fadeOut();
                }
            }

        } else {
            // idle 로 돌아감
            $(this).addClass('hidden');
            $(partySelector + '.motion-idle').removeClass('hidden').get(0).play();

            $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
            return true;
        }

        // 3타
        $(partySelector + '.motion-double-attack').one('ended', function () {
            console.log('triple fire')

            if (hitCount >= 3) {
                // 아군 공격모션
                $(partySelector + '.motion-double-attack').addClass('hidden');
                $(partySelector + '.motion-triple-attack').removeClass('hidden').get(0).play();

                // 적 피격모션
                $('.motion-enemy-idle').addClass('hidden');
                let motionDamagedElement = $('.motion-enemy-damaged').removeClass('hidden').get(0);
                motionDamagedElement.currentTime = 0;
                motionDamagedElement.play();

                // 효과음 재생
                let audioElement = $(partySelector + '-audio').get(0);
                audioElement.currentTime = 0;
                audioElement.play();

                // 데미지 표시
                $('.damage-third .origin-damage').fadeIn().delay('400').fadeOut();
                if (additionalHitCount > 0) {
                    $('.damage-third .damage-2').fadeIn().delay('400').fadeOut();
                    if (additionalHitCount > 1) {
                        $('.damage-third .damage-3').fadeIn().delay('400').fadeOut();
                    }
                }

            } else {
                $(partySelector + '.motion-double-attack').addClass('hidden');
                $(partySelector + '.motion-idle').removeClass('hidden').get(0).play();

                $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
                return true;
            }

            $(partySelector + '.motion-triple-attack').one('ended', function () {
                $(partySelector + '.motion-triple-attack').addClass('hidden');
                $(partySelector + '.motion-idle').removeClass('hidden').get(0).play();

                $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
                return true;
            })
        }) // 3타
    })// 2타
    //1타
    //종료
}// processParty