/**
 *  아군 캐릭터 하나의 공격행동 실행
 * @param charOrder 캐릭터의 순서
 * @param hitCount 캐릭터의 평타 타수
 * @param additionalHitCount 캐릭터의 추격갯수
 * @param audioPlayers 오디오 플레이어 맵, keys = 1, 2, 3, 4, enemy, global
 */
function processCharacterAttack(responseAttackData, charOrder) {

    console.log('[processCharacterAttack] attack start ' + charOrder)
    // 데미지 계산 및 채우기 선행
    // ...

    // 변수초기화
    let partySelector = '.party-' + charOrder
    // let attackMotionDelays = [0, 900, 1150, 1550];
    // let attackMotionDelays = [0, 700, 950, 1350];

    let attackData = responseAttackData;
    let moveType = MoveType.byName(attackData.moveType);
    let hitCount = attackData.hitCount; // 어빌리티 히트수 (피격모션, 데미지 표시관련)
    let damages = attackData.damages;
    let attackHitCount = damages.length;
    let additionalDamages = attackData.additionalDamages;
    let additionalAttackHitCount = additionalDamages.reduce((totalSize, damage) => totalSize + damage.length, 0);

    // 준비
    let $attackMotionVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className);
    let $idleMotionVideo = $('.party-video-container ' + partySelector + ' .' + MoveType.IDLE.className);

// EFFECT 이펙트 시작
    // 오디오 재생
    let audioSrc = $('.party-audio-container ' + partySelector + ' .' + moveType.className).attr('src');
    let audioPlayer = new AudioPlayer();
    audioPlayer.loadSound(audioSrc).then(() => {
        audioPlayer.playAllSounds();
    });

    // 아군 일반공격 이펙트 재생
    $idleMotionVideo.addClass('hidden'); // idle 모션 숨김
    $attackMotionVideo.removeClass('hidden').get(0).play();

    // 끝나고 모션 정상화
    $attackMotionVideo.one('ended', function () {
        $(this).addClass('hidden');
        $idleMotionVideo.removeClass('hidden');
    });

    // 데미지 채우기
    damages.forEach(function (damage, attackIndex) {
        let $attackDamage = $('<div>', {class: 'damage attack-damage', text: damage});
        if (additionalDamages[attackIndex]) {
            additionalDamages[attackIndex].forEach(function (additionalDamage, additionalIndex) {
                // 공격 타수마다 맞게 추격 붙여줌
                $attackDamage.append($('<div>', {class: 'damage additional-damage', text: additionalDamage}));
            })
        }
        $('.attack-damage-wrapper').append($attackDamage);
    })

    // 일반공격 이펙트 길이
    let attackDuration = $attackMotionVideo.get(0).duration * 1000 - 100; // ms 변환 및 100ms 영상보정
    let attackHitDuration = attackDuration / attackHitCount;

    console.log('attackhitcount', attackHitCount)

    // 데미지 표시, 적 피격 모션 재생 (히트수 만큼 반복)
    let attackHitPlayCount = 0;
    if (attackHitCount > 0) {
        // 적 idle 및 damaged 모션 클래스 찾기
        let standbyMoveClassName = $('.enemy-video-container').data('standby-move-class');
        let idleMoveClassName = standbyMoveClassName === 'none' ?
            MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdletype().className;
        let damagedMoveClassName = standbyMoveClassName === 'none' ?
            MoveType.DAMAGED_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getDamagedType().className;
        // 클래스로 비디오 찾기
        let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
        let $enemyDamagedVideo = $('.enemy-video-container .' + damagedMoveClassName);

        console.log('video', $enemyDamagedVideo, $enemyDamagedVideo)

        let attackHitProcessInterval = null;
        let hitIntervalCallback = function () {
            // idle 숨기고 damaged 재생
            !$enemyIdleVideo.hasClass('hidden') && $enemyIdleVideo.addClass('hidden'); // idle 숨김 (인터벌 전에 숨기면 플리커)
            let enemyDamagedVideoElement = $enemyDamagedVideo.removeClass('hidden').get(0);
            enemyDamagedVideoElement.currentTime = 0; // 빼면 부자연스러워짐
            enemyDamagedVideoElement.play();

            // 데미지 표시
            $('.attack-damage-wrapper .attack-damage').eq(attackHitPlayCount).fadeTo(10, 0.8).delay(600).fadeTo(400, 0);

            if (++attackHitPlayCount >= attackHitCount) {
                setTimeout(function () {
                    // 히트수만큼 재생 완료했으면 모션 정상화, 데미지 전체 제거 후 인터벌 클리어
                    $enemyIdleVideo.removeClass('hidden').get(0).play(); // 가끔 멈춰서 재생갱신
                    $enemyDamagedVideo.addClass('hidden');
                    setTimeout(function () {
                        $('.attack-damage-wrapper').children().remove();
                    }, 1000);
                    console.log('enemyduration', enemyDamagedVideoElement.duration)
                    clearInterval(attackHitProcessInterval);
                }, enemyDamagedVideoElement.duration * 1000 + 100); // 마지막 enemyDamagedVideoElement.play() 가 씹히는걸 방지
            }
        }

        hitIntervalCallback(); // 첫번째 즉시실행
        attackHitProcessInterval = attackHitPlayCount < attackHitCount ? setInterval(hitIntervalCallback, attackHitDuration) : null;
    }

    let totalEndTime = attackDuration;
    console.log("total = " + totalEndTime)
    return new Promise(resolve => setTimeout(function () {
        console.log('ATTACK done');
        resolve();
    }, totalEndTime));

    // 현재 일반공격의 스테이터스 갱신은 없음
}
