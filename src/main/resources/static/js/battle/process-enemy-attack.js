
function processEnemyNormalAttack(hitCount, additionalHitCount) {

    // 공격모션재생
    $('.motion-enemy-idle').addClass('hidden').get(0).pause();
    $('.motion-enemy-attack').removeClass('hidden').get(0).play();

    // 효과음 재생
    let audioElement = $('.enemy-audio-attack').get(0);
    audioElement.currentTime = 0;
    audioElement.play();

    // 데미지 표시
    $('.taken-damage-wrapper .origin-damage').fadeIn().delay(225).fadeOut();
    if (additionalHitCount > 0) {
        $('.taken-damage-wrapper .damage-2').fadeIn().delay(225).fadeOut();
        if (additionalHitCount > 1) {
            $('.taken-damage-wrapper .damage-3').fadeIn().delay(225).fadeOut();
        }
    }

    // 2타
    $('.motion-enemy-attack').one('ended', function () {
        if (hitCount > 1) {
            // 공격모션재생
            $('.motion-enemy-idle').addClass('hidden').get(0).pause();
            $('.motion-enemy-attack').removeClass('hidden').get(0).play();

            // 효과음 재생
            let audioElement = $('.enemy-audio-attack').get(0);
            audioElement.currentTime = 0;
            audioElement.play();

            // 데미지 표시
            $('.taken-damage-wrapper .origin-damage').fadeIn().delay(225).fadeOut();
            if (additionalHitCount > 0) {
                $('.taken-damage-wrapper .damage-2').fadeIn().delay(225).fadeOut();
                if (additionalHitCount > 1) {
                    $('.taken-damage-wrapper .damage-3').fadeIn().delay(225).fadeOut();
                }
            }
        } else {
            // idle 로 돌아감
            $(this).addClass('hidden');
            $('.motion-enemy-idle').removeClass('hidden').get(0).play();

            // 처리
            $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
        }

        // 3타
        $('.motion-enemy-attack').one('ended', function () {
            if (hitCount > 2) {
                // 공격모션재생
                $('.motion-enemy-idle').addClass('hidden').get(0).pause();
                $('.motion-enemy-attack').removeClass('hidden').get(0).play();

                // 효과음 재생
                let audioElement = $('.enemy-audio-attack').get(0);
                audioElement.currentTime = 0;
                audioElement.play();

                // 데미지 표시
                $('.taken-damage-wrapper .origin-damage').fadeIn().delay(225).fadeOut();
                if (additionalHitCount > 0) {
                    $('.taken-damage-wrapper .damage-2').fadeIn().delay(225).fadeOut();
                    if (additionalHitCount > 1) {
                        $('.taken-damage-wrapper .damage-3').fadeIn().delay(225).fadeOut();
                    }
                }

                $('.motion-enemy-attack').one('ended', function () {
                    // idle 로 돌아감
                    $(this).addClass('hidden');
                    $('.motion-enemy-idle').removeClass('hidden').get(0).play();

                    // 처리
                    $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
                });

            } else {
                // idle 로 돌아감
                $(this).addClass('hidden');
                $('.motion-enemy-idle').removeClass('hidden').get(0).play();

                // 처리
                $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
            }
        }) // 3타
    })// 2타
    // 1타

}

function processEnemyChargeAttack(damageDelay) {
    // 공격모션재생
    $('.motion-enemy-idle').addClass('hidden').get(0).pause();
    $('.motion-enemy-charge-attack-c').removeClass('hidden').get(0).play();

    // 효과음 재생
    let audioElement = $('.enemy-audio-charge-attack-c-1').get(0);
    audioElement.currentTime = 0;
    audioElement.play();
    audioElement.addEventListener('ended', function () {
        let audioElement = $('.enemy-audio-charge-attack-c-2').get(0);
        audioElement.currentTime = 0;
        audioElement.play();
    });

    // 데미지 표시
    $('.taken-damage-wrapper .origin-damage').fadeOut().delay(damageDelay).fadeIn().delay(225).fadeOut();

    $('.motion-enemy-charge-attack-c').one('ended', function () {
        // idle 로 돌아감
        $(this).addClass('hidden');
        $('.motion-enemy-idle').removeClass('hidden').get(0).play();

        $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
    })

}