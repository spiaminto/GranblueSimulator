function processAttack(response) {
    let attackHitCount = response.damages.length; // 공격 데미지 발생수
    let multiAttackCount = response.attackMultiHitCount; // 난격 수
    let attackCount = attackHitCount / multiAttackCount; // 본 공격 카운트 (1 || 2 || 3, 난격제외)
    let elementType = response.elementTypes[0];

    // 준비 - 아군
    let $partyVideo = getVideo(response.charOrder, response.moveType);
    if ($partyVideo.motion == null) $partyVideo.effect.addClass('large'); // 모션이 없는 공격은 900p -> large 클래스 추가
    let effectDuration = $partyVideo.effect.get(0).duration * 1000;
    // 준비 - 아군 (난격) : 난격이 붙은경우 이펙트, 모션을 전체적으로 천천히 재생한다.
    let playBackRate = 1.0 - 0.1 * (multiAttackCount - 1); // 난격시 느리게
    $partyVideo.effect.get(0).playbackRate = playBackRate;
    if ($partyVideo.motion) $partyVideo.motion.get(0).playbackRate = playBackRate;
    effectDuration = Math.floor(effectDuration / playBackRate); // 재생 속도에 따른 길이 보정
    let attackDelay = Math.floor(attackHitCount / multiAttackCount >= 3 ? Math.max((effectDuration - 400), 1100) / attackCount : effectDuration / attackCount); // 기본 타수당 딜레이, 난격에 다른 속도 지정후 계산 (약 350ms, 보통 평타 이펙트의 길이가 350 350 900 쯤 된다), 3타시 최소 1100ms 보장
    // console.log('processAttack charOrder = ', charOrder, ' playbackRATE = ', playBackRate, ' attackDuration', attackDuration);

    // 준비 - 적
    let $enemyVideo = getEnemyDamagedVideo();

    // 오디오 재생
    let audioSrcs = [];
    audioSrcs.push($('#audioContainer .actor-' + response.charOrder + '.' + response.moveType.className + ".voice").attr('src')); // 보이스
    if (multiAttackCount > 1) {
        // 난격이면 타수만큼 싱글어택 사운드 준비
        let effectSrc = $('#audioContainer .actor-' + response.charOrder + '.' + MoveType.SINGLE_ATTACK.className + ".effect").attr('src');
        audioSrcs.push(...Array(attackHitCount).fill(effectSrc));
    } else {
        let effectSrc = $('#audioContainer .actor-' + response.charOrder + '.' + response.moveType.className + ".effect").attr('src');
        audioSrcs.push(effectSrc);
    }
    window.effectAudioPlayer.loadSounds(audioSrcs).then(() => {
        window.effectAudioPlayer.playAttackSounds(attackHitCount, multiAttackCount, attackDelay);
    });

    // 아군 일반공격 이펙트 재생
    playVideo($partyVideo.effect, $partyVideo.motion, $partyVideo.idle);

    // 데미지 채우기
    let currentAttackDamageWrapperIndex = $('.attack-damage-wrapper.actor-' + response.charOrder).length; // 남아있는 이전 공격데미지 래퍼 (겹침방지 새로생성용)
    let $attackDamageWrapper = $('<div>', {class: 'attack-damage-wrapper party actor-' + response.charOrder + ' attack-index-' + currentAttackDamageWrapperIndex});
    response.damages.forEach(function (damage, index) {
        let attackIndex = Math.floor(index / multiAttackCount); // 현재 인덱스의 기본타수 순서 (0, 1, 2 현재 트리플 어택까지 구현했으므로 여기까지)
        let multiAttackIndex = index % multiAttackCount; // 현재 인덱스의 난격 타수 순서 (공격마다 0-1-2-3-... 씩으로 진행, 0이면 난격이 아님)
        let missClassName = damage === 'MISS' ? ' damage-miss' : '';

        // 난격 여부 확인 후 클래스 설정 / [012 345 678] 3타 3난격 시 di-0 m-0, di-0 m-1, di-0 m-2, di-1 m-0, di-1 m-1, ...
        let attackDamageIndexClassName = ' damage-index-' + attackIndex + ' multi-' + multiAttackIndex;

        let $attackDamage = $('<div>', { // 본 공격 + 난격
            class: 'attack-damage actor-' + response.charOrder + ' element-type-' + elementType.toLowerCase() + attackDamageIndexClassName + missClassName,
            text: damage,
        });
        $attackDamageWrapper.append($attackDamage);
        let $additionalDamage = $('<div>', { // 추격
            class: 'additional-damage-wrapper actor-' + response.charOrder + ' element-type-' + elementType.toLowerCase() + attackDamageIndexClassName + missClassName,
            text: damage // 공간 사용을 위해
        }).append((response.additionalDamages[index] || []).map(additionalDamage =>
            $('<div>', {
                class: 'additional-damage element-type-' + elementType.toLowerCase(),
                text: additionalDamage
            })
        ))
        $attackDamageWrapper.append($additionalDamage);
        // 마지막에 DOM 에 추가
        index >= attackHitCount - 1 ? $('#attackDamageContainer').append($attackDamageWrapper) : null;
    });

    // 데미지 표시, 적 피격 모션 재생 (히트수 만큼 반복)
    let $attackDamages = $attackDamageWrapper.find('.attack-damage');
    let $additionalDamages = $attackDamageWrapper.find('.additional-damage-wrapper');
    response.damages.forEach(function (damage, index) {
        // 데미지 채우기
        let attackIndex = Math.floor(index / multiAttackCount); // 현재 인덱스의 기본타수 순서 (0, 1, 2 현재 트리플 어택까지 구현했으므로 여기까지)
        let multiAttackIndex = index % multiAttackCount; // 현재 인덱스의 난격 타수 순서 (공격마다 0-1-2-3-... 씩으로 진행, 0이면 난격이 아님)
        let startDelay = attackDelay * attackIndex; // 1타당 시작시 걸어줄 딜레이, 난격이 있을경우 난격마다 딜레이가 같아짐 ([1,2,3,4,5,6] 2회난격시 승수가 0, 0, 1, 1, 2, 2)
        let multiHitDelay = multiAttackIndex * 115; // 난격마다 추가 딜레이
        let totalDelay = startDelay + multiHitDelay; // 최종 딜레이
        // console.log('[processAttack] baseDelay = ', attackDelay, ' startDelay = ', startDelay, ' multiHitDealy = ', multiHitDelay, ' totalDelay = ', totalDelay);

        let $attackDamage = $attackDamages.eq(index);
        let $additionalDamage = $additionalDamages.eq(index);
        setTimeout(function () {
            // 적 피격 이펙트 재생 (난격일땐 재생 x)
            multiAttackIndex === 0 ? playVideo($enemyVideo.effect, null, $enemyVideo.idle) : null;
            // 데미지 표시
            $attackDamage.addClass('party-attack-damage-show');
            // 추가데미지 표시
            $additionalDamage.children().each(function (index, additionalDamage) {
                setTimeout(function () {
                    $(additionalDamage).addClass('party-additional-damage-show'); // 추가데미지는 부모와의 fadeIn 겹침을 피하기 위해 display none 설정되어있음
                }, 50 * (index + 1));
            });

            if (index >= attackHitCount - 1) {
                setTimeout(function () {
                    $attackDamageWrapper.remove(); // 이번 공격의 데미지 래퍼 삭제
                }, 1500);
            }
        }, totalDelay)
    })

    // 일반공격후 스테이터스 갱신은 현재 없음.

    let totalEndTime = effectDuration;
    console.log("[processAttack] totalEndTime = " + totalEndTime)
    // 최종종료
    return new Promise(resolve => setTimeout(function () {
        console.log('ATTACK done');
        resolve();
    }, totalEndTime + 300));
}


function processEnemyAttack(response) {
    let attackCount = response.moveType === MoveType.SINGLE_ATTACK ? 1 : response.moveType === MoveType.DOUBLE_ATTACK ? 2 : 3;
    // let targetOrders = response.enemyAttackTargetOrders;
    // let uniqueTargetOrders = [...new Set(targetOrders)];

    // 준비 - 적
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveType = standbyMoveClassName === 'none' ? MoveType.IDLE_DEFAULT : MoveType.byClassName(standbyMoveClassName).getIdleType();
    let $enemyVideo = getVideo(0, MoveType.SINGLE_ATTACK, idleMoveType)
    let effectDuration = $enemyVideo.effect.get(0).duration * 1000;
    let effectTotalDuration = effectDuration * attackCount;
    // 준비 - 아군 (0번은 사용안함)
    let $partyVideos = [-1, 1, 2, 3, 4]
        .map(number => getVideo(number, MoveType.DAMAGED_DEFAULT, MoveType.IDLE_DEFAULT));

// EFFECT 이펙트 시작
    // 오디오 재생
    let audioSrc = $('.enemy-audio-container .' + response.moveType.className).attr('src');
    window.effectAudioPlayer.loadSound(audioSrc).then(() => window.effectAudioPlayer.playAllSounds());

    // 적 일반공격 이펙트 재생
    $enemyVideo.effect.removeClass('hidden');
    $enemyVideo.idle.addClass('left-hidden');
    $enemyVideo.idle.get(0).pause();
    $enemyVideo.idle.get(0).currentTime = 0;
    for (let i = 0; i < attackCount; i++) {
        setTimeout(function () {
            $enemyVideo.effect.get(0).currentTime = 0;
            $enemyVideo.effect.get(0).play();

            if (i === attackCount - 1) { // 마지막
                setTimeout(function () {
                    requestAnimationFrame(function () {
                        $enemyVideo.idle.removeClass('left-hidden');
                        $enemyVideo.idle.get(0).play();
                        setTimeout(function () {
                            $enemyVideo.effect.addClass('hidden');
                        }, 50)
                    })
                }, effectDuration)
            }
        }, effectDuration * i)
    }

    enemyDamagesPostProcess(response, $enemyVideo, $partyVideos);

    let totalEndTime = effectTotalDuration;
    console.log("[processEnemyAttack] total = " + totalEndTime)
    return new Promise(resolve => setTimeout(function () {
        console.log('ENEMY ATTACK done');
        resolve();
    }, totalEndTime + 300));

// 현재 일반공격의 스테이터스 갱신은 없음
}
