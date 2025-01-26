function processTurn() {
    let finished = false;
    $('#processingTask').children().first().prop('checked', true);

    //TODO 통신
    let responseData = null;
    let isChargeAttack = [false, false, true, false, true]; // charOrder 로 조회하는것을 상정

    let audioPlayers = new Map();
    audioPlayers.set(1, new AudioPlayer());
    audioPlayers.set(2, new AudioPlayer());
    audioPlayers.set(3, new AudioPlayer());
    audioPlayers.set(4, new AudioPlayer());
    audioPlayers.set('enemy', new AudioPlayer());
    audioPlayers.set('global', new AudioPlayer());

    // 천번째 캐릭터 공격행동 실행
    let firstCharacterAttackAudioSrc = $('.party-1-audio').attr('src');
    console.log('first = ' + firstCharacterAttackAudioSrc)
    audioPlayers.get(1).loadSound(firstCharacterAttackAudioSrc).then(() => {
        if (isChargeAttack[1]) {
            processChargeAttack(1, audioPlayers);
        } else {
            processCharacterAttack(1, 3, 3, audioPlayers);
        }
    });

    // 첫번째 캐릭터 공격행동 이후 실행
    $("#processingTask input[name='processingTask']").one('change', function () {
        // 다음 프로세스 정보
        let processingTaskVal = $("#processingTask input[name='processingTask']:checked").val();

        // 딜레이 후 실행
        setTimeout(function () {
            if (processingTaskVal === 'firstCharacterAttackPost') {
                processCharacterAttackPost(1, null);
            }

            if (processingTaskVal === 'secondCharacterAttack') {
                if (isChargeAttack[2]) {
                    processChargeAttack(2, audioPlayers);
                } else {
                    processCharacterAttack(2, 3, 3, audioPlayers);
                }
            }

            if (processingTaskVal === 'secondCharacterAttackPost') {
                processCharacterAttackPost(2, null);
            }

            if (processingTaskVal === 'thirdCharacterAttack') {
                if (isChargeAttack[3]) {
                    processChargeAttack(3, audioPlayers);
                } else {
                    processCharacterAttack(3, 3, 3, audioPlayers);
                }
            }

            if (processingTaskVal === 'thirdCharacterAttackPost') {
                processCharacterAttackPost(3, null);
            }

            if (processingTaskVal === 'fourthCharacterAttack') {
                if (isChargeAttack[4]) {
                    processChargeAttack(4, audioPlayers);
                } else {
                    processCharacterAttack(4, 3, 3, audioPlayers);
                }
            }

            if (processingTaskVal === 'fourthCharacterAttackPost') {
                processCharacterAttackPost(4, null);
            }

            if (processingTaskVal === 'enemyAttack') {
                processEnemyAttack(3, 3);
            }

            if (processingTaskVal === 'enemyAttackPost') {
                processEnemyAttackPost();
            }

            if (processingTaskVal === 'turnPost') {
                processTurnPost();
            }

        }, 100)

    })
}

function processChargeAttack(charOrder, audioPlayers) {

    let partySelector = '.party-' + charOrder;

    // 아군 공격모션
    let $chargeAttackMotionVideo = $(partySelector + '.motion-charge-attack');
    console.log($chargeAttackMotionVideo)
    setTimeout(function () {
        $(partySelector + '.motion-idle').addClass('hidden');
        $chargeAttackMotionVideo.removeClass('hidden').addClass('motion-attack-active').get(0).play();
    }, 300);
    $chargeAttackMotionVideo.one('ended', function () {
        console.log('video-ended')

        // 데미지 표시
        $('.enemy-charge-attack-damage').fadeTo(100, 0.8).delay(500).fadeTo(300, 0);

        $(this).addClass('hidden').removeClass('motion-attack-active');
        $(partySelector + '.motion-idle').removeClass('hidden');

        // 데미지 표시 종료후 오의어택종료
        setTimeout(() => {
            $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
        }, 1000);
    })

    // 효과음 재생
    let audioInfos = $(partySelector + '-audio.charge-attack').toArray().map(element => ({
        url: $(element).attr('src'),
        delay: $(element).data('delay') || 0
    }));
    console.log(audioInfos);
    audioPlayers.get(charOrder).loadSounds(audioInfos).then(() => {
        audioPlayers.get(charOrder).playAllSounds();
    })

    // TODO 적 오의 데미지 채우기
}

function processCharacterAttackPost(order, data) {
    // 후처리
    $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
}

let i = 1;

function processEnemyAttack() {
    if (i % 2 === 1) {
        processEnemyNormalAttack(3, 3);
    } else {
        processEnemyChargeAttack(3000);
    }
    i++;
}

function processEnemyAttackPost() {
    // 처리
    $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
}

function processTurnPost() {
    //처리
    $("#processingTask input[name = 'processingTask']:checked").prop('checked', '') // 초기화
}