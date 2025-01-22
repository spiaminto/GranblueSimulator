
function processTurn() {
    let finished = false;
    $('#processingTask').children().first().prop('checked', true);

    let audioPlayers = new Map();
    audioPlayers.set(1, new AudioPlayer());
    audioPlayers.set(2, new AudioPlayer());
    audioPlayers.set(3, new AudioPlayer());
    audioPlayers.set('enemy', new AudioPlayer());
    audioPlayers.set('global', new AudioPlayer());

    let firstCharacterAttackAudioSrc = $('.party-1-audio').attr('src');
    console.log('first = ' + firstCharacterAttackAudioSrc)
   audioPlayers.get(1).loadSound(firstCharacterAttackAudioSrc).then(() => {
        processCharacterAttack(1, 3, 3, audioPlayers);
    });

    $("#processingTask input[name='processingTask']").one('change', function () {
        // 다음 프로세스 정보
        let processingTaskVal = $("#processingTask input[name='processingTask']:checked").val();

        // 딜레이 후 실행
        setTimeout(function () {
            if (processingTaskVal === 'firstCharacterAttackPost') {
                processCharacterAttackPost(1, null);
            }

            if (processingTaskVal === 'secondCharacterAttack') {
                processCharacterAttack(2, 3, 3, audioPlayers);
            }

            if (processingTaskVal === 'secondCharacterAttackPost') {
                processCharacterAttackPost(2, null);
            }

            if (processingTaskVal === 'thirdCharacterAttack') {
                processCharacterAttack(3, 3, 3, audioPlayers);
            }

            if (processingTaskVal === 'thirdCharacterAttackPost') {
                processCharacterAttackPost(3, null);
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

function processCharacterAttackPost(order, data) {
    // 후처리
    $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
}

let i = 1;
function processEnemyAttack () {
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