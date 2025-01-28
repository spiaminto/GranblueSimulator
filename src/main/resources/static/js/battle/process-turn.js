function processTurn() {
    let finished = false;
    // $('#processingTask').children().first().prop('checked', true);

    //TODO 통신
    let responseData = null;
    let isChargeAttack = [false, true, true, false]; // charOrder 로 조회하는것을 상정
    let attackHitCount = [1, 1, 1, 1];
    let additionalHitCount = [1, 1, 1, 1];

    // 오디오 플레이어 로드
    let audioPlayers = new Map();
    let normalAttackSrcs = $('audio.normal-attack').toArray().map(element => element.src);
    audioPlayers.set(1, isChargeAttack[0] ? new AudioPlayer(normalAttackSrcs[0]) : new AudioPlayer());
    audioPlayers.set(2, isChargeAttack[1] ? new AudioPlayer(normalAttackSrcs[1]) : new AudioPlayer());
    audioPlayers.set(3, isChargeAttack[2] ? new AudioPlayer(normalAttackSrcs[2]) : new AudioPlayer());
    audioPlayers.set(4, isChargeAttack[3] ? new AudioPlayer(normalAttackSrcs[3]) : new AudioPlayer());
    audioPlayers.set('enemy', new AudioPlayer());
    audioPlayers.set('global', new AudioPlayer());

    // 진행 이벤트 1회씩 등록
    $("#processingTask input[name='processingTask']").one('change', function () {
        // 다음 프로세스 정보
        let processingTaskVal = $("#processingTask input[name='processingTask']:checked").val();
        console.log('processingTaskVal = ', processingTaskVal)

        // 딜레이 후 실행
        setTimeout(function () {

            if (processingTaskVal === 'firstCharacterAttack') {
                if (isChargeAttack[0]) {
                    processChargeAttack(1, audioPlayers);
                } else {
                    processCharacterAttack(1, 1, 3, audioPlayers);
                }
            }

            if (processingTaskVal === 'firstCharacterAttackPost') {
                processCharacterAttackPost(1, null);
            }

            if (processingTaskVal === 'secondCharacterAttack') {
                if (isChargeAttack[1]) {
                    processChargeAttack(2, audioPlayers);
                } else {
                    processCharacterAttack(2, 1, 3, audioPlayers);
                }
            }

            if (processingTaskVal === 'secondCharacterAttackPost') {
                processCharacterAttackPost(2, null);
            }

            if (processingTaskVal === 'thirdCharacterAttack') {
                if (isChargeAttack[2]) {
                    processChargeAttack(3, audioPlayers);
                } else {
                    processCharacterAttack(3, 1, 3, audioPlayers);
                }
            }

            if (processingTaskVal === 'thirdCharacterAttackPost') {
                processCharacterAttackPost(3, null);
            }

            if (processingTaskVal === 'fourthCharacterAttack') {
                if (isChargeAttack[3]) {
                    processChargeAttack(4, audioPlayers);
                } else {
                    processCharacterAttack(4, 1, 3, audioPlayers);
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

        }, 200)
    }) // 진행이벤트 등록
    $('#processingTask #firstCharacterAttack').click(); // 첫번째 진행
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