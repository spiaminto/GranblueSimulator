/**
 * 데미지 요소를 만들어반환
 * 일반적으로, 해당 데미지요소는 XXDamgeWrapper 에 append 됨.
 * 난격을 포함하는 캐릭터 일반공격은 대응하지 않음.
 * @param charOrder
 * @param elementType
 * @param type 데미지 타입, 'attack', 'ability', chargeAttack'
 * @param index 데미지 인덱스
 * @param damage
 * @param additionalDamages
 * @param isEnemy optional
 * @returns {{$damage: (*|jQuery), $additionalDamage: (*|jQuery)}}
 */
function getDamageElement(charOrder, elementType, type, index, damage, additionalDamages, isEnemy = false) {
    let typeClassName;
    switch (type) {
        case 'attack':
            typeClassName = ' attack-damage';
            break;
        case 'ability':
            typeClassName = ' ability-damage';
            break;
        case 'chargeAttack':
            typeClassName = ' charge-attack-damage';
            break;
        default:
            new Error('[getDamageElement] invalid type, type = ' + type);
    }
    let actorClassName = ' actor-' + charOrder;
    let elementClassName = ' element-type-' + elementType.toLowerCase();
    let damageIndexClassName = ' damage-index-' + index;
    let enemyClassName = isEnemy ? ' enemy' : '';
    let missClassName = damage === 'MISS' ? ' damage-miss' : '';


    // 데미지 요소
    let $damage = $('<div>', {
        class: typeClassName + actorClassName + elementClassName + damageIndexClassName + elementClassName + missClassName + enemyClassName,
        text: damage
    });
    // 추격 요소
    let $additionalDamage = $('<div>', {
        class: 'additional-damage-wrapper ' + enemyClassName + actorClassName + elementClassName + missClassName,
        text: additionalDamages
    }).append((additionalDamages || []).map(additionalDamage =>  // 추격이 존재하면 붙임
        $('<div>', {
            class: 'additional-damage' + elementClassName,
            text: additionalDamage
        })
    ));
    return {$damage, $additionalDamage};
}


/**
 * 커맨드 패널의 현재 스테이터스 아이콘 갱신 (이펙트 종료 직후)
 * @param currentBattleStatusesList 갱신할 현재 스테이터스리스트
 * @param effectVideoDuration 이펙트 길이 (시작딜레이로 사용)
 */
function processStatusIconSync(currentBattleStatusesList, effectVideoDuration) {
    // 스테이터스 아이콘 갱신 (어빌리티 이펙트 직후 즉시 갱신)
    setTimeout(function () {
        currentBattleStatusesList.forEach(function (currentBattleStatuses, actorIndex) {
            let $statusContainer = $('.status-container.actor-' + actorIndex);
            $statusContainer.find('.status').remove(); // 스테이터스 비움
            currentBattleStatuses.forEach(function (status, index) {
                // 어빌리티 패널에 갱신된 스테이터스 추가
                let $statusInfo = $('<div>', {class: 'status status', 'data-status-type': status.type})
                    .append(
                        $('<img>', {
                            src: status.imageSrc,
                            class: 'status-icon' + (status.imageSrc.length < 1 ? ' none-icon' : '')
                        }),
                        $('<div>', {class: 'status-name d-none', text: status.name}),
                        $('<div>', {class: 'status-info-text d-none', text: status.statusText}),
                        $('<div>', {class: 'status-duration d-none', text: status.duration})
                    );
                $statusContainer.append($statusInfo);
            })
        });
    }, effectVideoDuration);
}

function processHealEffect(healArray, effectVideoDuration) {
    let healDelay = effectVideoDuration;
    if (healArray.length === 0 || healArray.reduce((a, b) => a + b, 0) === 0) return healDelay;
    let audioPlayer = new AudioPlayer().init();
    let $healValueWrappers = [];
    healArray.forEach(function (heal, index) { // 적의 데미지 컨테이너를 힐 량 표시 래퍼로 사용
        $healValueWrappers.push($('<div>', {class: 'enemy-damage-wrapper actor-' + index}).appendTo($('#damageContainer')));
    })
    healArray.forEach(function (heal, actorIndex) {
        // console.log('[processHealEffect] heal = ', heal, ' actorIndex = ', actorIndex);
        if (heal !== 0) {
            setTimeout(function () {
                // 이펙트 재생
                let $healVideo = $('.heal-video-wrapper .heal.actor-' + actorIndex);
                playVideo($healVideo, null, null);
                // 사운드 재생
                audioPlayer.loadSound($('.global-audio-container .heal').attr('src')).then(function () {
                    audioPlayer.playAllSoundsWithoutClear();
                });
                // 데미지채우기
                let $healValueWrapper = $healValueWrappers[actorIndex];
                let $healElements = getDamageElement(actorIndex, 'NONE', 'attack', 0, heal, []);
                $healValueWrapper.append($healElements.$damage);
                setTimeout(function () {
                    $healElements.$damage.addClass('heal enemy-damage-show'); // 약간 느리게
                }, 100)
            }, effectVideoDuration + 100 * actorIndex);
            healDelay = effectVideoDuration + 800 + 100 * actorIndex; // 버프 시작할 딜레이 지정
        }
        if (actorIndex === healArray.length - 1) { // 힐 (데미지 요소) 삭제
            setTimeout(function () {
                $healValueWrappers.forEach(function ($healValueWrapper, index) {
                    $healValueWrapper.remove();
                })
            }, effectVideoDuration + 1500);
        }
    })
    return healDelay;
}

/**
 * 버프 이펙트를 처리
 * @param addedBuffStatusesList 추가된 버프 스테이터스 리스트 (빈 배열 가능)
 * @param removedBuffStatusesList
 * @param removedDebuffStatusesList
 * @param effectVideoDuration 이펙트 비디오 길이 (시작 전 딜레이)
 * @returns longestBuffEndTime 가장 긴 버프 끝시간 (다음 시작 딜레이로 사용)
 */
function processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, effectVideoDuration) {
    let longestBuffEndTime = effectVideoDuration; // 버프 없을땐 이전 딜레이 (이펙트 딜레이) 만큼
    let statusesList = addedBuffStatusesList.map((_, idx) => {
            const removedDebuffs = (removedDebuffStatusesList[idx] || []).map(x => ({value: x, type: 'removedDebuffs'}));
            const addedBuffes = (addedBuffStatusesList[idx] || []).map(x => ({value: x, type: 'addedBuffs'}));
            const removedBuffes = (removedBuffStatusesList[idx] || []).map(x => ({value: x, type: 'removedBuffs'}));
            return [...removedDebuffs, ...addedBuffes, ...removedBuffes];
        }
    );
    // console.log('[processBuffEffect] statusesList = {}', statusesList);
    // 표시 순서 removedDebuff -> addedBuff -> removedBuff
    statusesList.forEach(function (statuses, actorIndex) { // [[적][아군][아군][아군][아군]]
        let statusRemovedEffectCount = 0;
        statuses.forEach(function (statusObject, statusIndex) {
            let status = statusObject.value;
            let $effectContainer = $('.status-effect-container.actor-' + actorIndex);
            let $statusEffect = $('<div>', {class: 'status-effect status-effect-' + statusIndex})
                .append(
                    $('<img>', {src: status.imageSrc, class: status.imageSrc.length < 1 ? 'none-icon' : ''}),
                    $('<span>', {class: 'status-effect-text', text: status.effectText})
                );
            $effectContainer.append($statusEffect);

            let type = statusObject.type;
            let audioPlayer = null;
            let $statusRemovedEffect = null;
            if (type === 'removedDebuffs' || type === 'removedBuffs') {
                audioPlayer = new AudioPlayer().init();
                audioPlayer.loadSound($('.global-audio-container .status-removed').attr('src')); // 일단 실행까지 여유가 있으니 놔둠

                let statusEffectPosition = $statusEffect.position();
                $statusRemovedEffect = $('<div>', {class: 'status-effect status-removed-effect'}).css({
                    top: statusEffectPosition.top - 5,
                    left: statusEffectPosition.left - 10,
                    width: $statusEffect.find('.status-effect-text').width() + 30, // img 크기까지
                    height: $statusEffect.find('.status-effect-text').height()
                });
                $effectContainer.append($statusRemovedEffect);
                statusRemovedEffectCount++;
            }

            let statusForPage = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 스테이터스 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
            let statusPageCount = Math.floor(statusIndex / statusForPage); // 현재 표시할 스테이터스의 페이지 (0 부터)
            let startDelay = effectVideoDuration + (1100 * statusPageCount) + (50 * (statusIndex % statusForPage)); // 페이드 길이 1100,
            longestBuffEndTime = Math.max(longestBuffEndTime, startDelay + 1100); // 마지막 버프 이펙트 끝나는 시간

            setTimeout(() => {
                if (audioPlayer != null && $statusRemovedEffect != null) {
                    audioPlayer.playAllSoundsWithoutClear();
                    $statusEffect.addClass('status-removed');
                    $statusRemovedEffect.addClass('active');
                } else {
                    $statusEffect.fadeTo(100, 1).delay(800).fadeTo(200, 0);
                }

                if (statusIndex >= statuses.length - 1 || statusIndex % statusForPage + 1 === statusForPage) {
                    // 마지막 스테이터스 또는 페이지 마지막마다 제거
                    setTimeout(() => $effectContainer.find('.status-effect').slice(0, statusIndex % statusForPage + 1).remove(), 1100);
                    statusRemovedEffectCount = 0;
                }
            }, startDelay);
        });
    });
    return longestBuffEndTime;
}

/**
 * 디버프 이펙트 처리
 * @param addedDebuffStatusesList
 * @param debuffStartDelay 시작딜레이 (이펙트딜레이 + 버프딜레이)
 * @returns longestDebuffEndTime 가장 긴 디버프 딜레이 (다음 시작 딜레이로 사용)
 */
function processDebuffEffect(addedDebuffStatusesList, debuffStartDelay) {
    let longestDebuffEndTime = debuffStartDelay; // 디버프 없을댄 이전 딜레이 (이펙트 + 버프 딜레이) 만큼
    addedDebuffStatusesList.forEach(function (addedDebuffStatuses, actorIndex) { // [1번째 캐릭] [2번째 캐릭]...
        addedDebuffStatuses.forEach(function (debuffStatus, debuffIndex) {
            let $effectContainer = $('.status-effect-container.actor-' + actorIndex);
            let $statusEffect = $('<div>', {class: 'status-effect status-effect-' + debuffIndex})
                .append(
                    $('<img>', {
                        src: debuffStatus.imageSrc,
                        class: debuffStatus.imageSrc.length < 1 ? 'none-icon' : ''
                    }),
                    $('<span>', {class: 'status-effect-text', text: debuffStatus.effectText})
                );
            $effectContainer.append($statusEffect);

            let debuffForPage = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 디버프 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
            let debuffPageCount = Math.floor(debuffIndex / debuffForPage); // 현재 표시할 디버프의 페이지 (0 부터)
            let startDelay = debuffStartDelay + (1100 * debuffPageCount) + (50 * (debuffIndex % debuffForPage)); // 페이드 길이 1100
            longestDebuffEndTime = Math.max(longestDebuffEndTime, startDelay + 1100); // 마지막 디버프 이펙트 끝나는 시간

            setTimeout(() => {
                $statusEffect.fadeTo(100, 1).delay(800).fadeTo(200, 0, function () {
                    $effectContainer.find('.status-effect').slice(0, debuffForPage).remove();
                });
            }, startDelay);
        });
    });
    return longestDebuffEndTime;
}

/**
 * 파라미터로 넘어온 actorOrder, moveType, idleType 에 맞는 비디오 찾아서 오브젝트로 반환
 * @param actorOrder
 * @param moveType
 * @param idleType optional, 아군의 경우 없어도됨. 적의경우 idle default 가 아닌경우 사용
 * @returns {{motion: (jQuery|HTMLElement|*), effect: (jQuery|HTMLElement|*), idle: (jQuery|HTMLElement|*)}}, 없으면 null 로 대체됨
 */
function getVideo(actorOrder, moveType, idleType = MoveType.IDLE_DEFAULT) {
    let $effectVideo = $('#videoContainer .actor-' + actorOrder + '.' + moveType.className + '.effect').eq(0);
    let $motionVideo = $('#videoContainer .actor-' + actorOrder + '.' + moveType.className + '.motion').eq(0);
    let $idleVideo = $('#videoContainer .actor-' + actorOrder + '.' + idleType.className).eq(0);
    // 없으면 null 로 대체
    $effectVideo = $effectVideo.length > 0 ? $effectVideo : null;
    $motionVideo = $motionVideo.length > 0 ? $motionVideo : null;
    $idleVideo = $idleVideo.length > 0 ? $idleVideo : null;
    return {
        effect: $effectVideo,
        motion: $motionVideo,
        idle: $idleVideo
    }
}

/**
 * 적 피격 동영상용 별도 getVideo
 * @returns {{motion: (Window.jQuery|HTMLElement|*), effect: (Window.jQuery|HTMLElement|*), idle: (Window.jQuery|HTMLElement|*)}}
 */
function getEnemyDamagedVideo() {
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveType = standbyMoveClassName === 'none' ? MoveType.IDLE_DEFAULT : MoveType.byClassName(standbyMoveClassName).getIdleType();
    let damagedMoveType = standbyMoveClassName === 'none' ? MoveType.DAMAGED_DEFAULT : MoveType.byClassName(standbyMoveClassName).getDamagedType();
    return getVideo(0, damagedMoveType, idleMoveType);
}


/**
 * 비디오를 재생하는 메서드
 * effectVideo, motionVideo 는 hidden 없앤 후 재생, 완료후 hidden
 * idleVideo 는 left-hidden 으로 왼쪽으로 뺀 후 재생 완료후 다시 돌려놓음
 *
 * CHECK 여기서 한번 더 분기필요하면 아얘 분리
 * @param $effectVideo
 * @param $motionVideo optional
 * @param $idleVideo optional
 */
function playVideo($effectVideo, $motionVideo, $idleVideo) {
    // console.log('[playVideo] $effectVideo = ', $effectVideo, ' $motionVideo = ', $motionVideo, ' $idleVideo = ', $idleVideo)
    if ($effectVideo == null) {
        console.warn('effectVideo is null');
        return; // 이펙트 비디오 없으면 무시
    }
    if ($effectVideo?.length === 0 || $motionVideo?.length === 0 || $idleVideo?.length === 0) {
        throw new Error('video length is 0') // 비디오는 있거나, null 이거나 둘중하나
    }

    // 모션 있으면 재생
    if ($motionVideo) {
        $motionVideo
            .one('playing', function () {
                requestAnimationFrame(function () {
                    // idleVideo 는 effectVideo 에서 처리
                    $motionVideo.removeClass('hidden');
                })
            }).get(0).play();
    }

    // 이펙트 있으면 재생
    requestAnimationFrame(function () {
        $effectVideo.removeClass('hidden');
        $idleVideo?.addClass('left-hidden').removeClass('hidden');
        if ($idleVideo) $idleVideo.get(0).currentTime = 0;
        $idleVideo?.get(0).pause();
    })
    $effectVideo.one('ended', function () {
        if ($idleVideo) {
            requestAnimationFrame(function () {
                $idleVideo.removeClass('left-hidden');
                $idleVideo.get(0).play();
                requestAnimationFrame(function () {
                    $effectVideo.addClass('hidden');
                    $motionVideo?.addClass('hidden');
                })
            })
        } else {
            $motionVideo?.addClass('hidden');
            $effectVideo.addClass('hidden');
        }
    }).get(0).play();
}

// 응답
// 상태 정보 클래스
class StatusDto {
    constructor({type, name, imageSrc, effectText, statusText, duration}) {
        this.type = type;
        this.name = name;
        this.imageSrc = imageSrc;
        this.effectText = effectText;
        this.statusText = statusText;
        this.duration = duration;
    }
}

// MoveResponse 클래스
class MoveResponse {
    constructor(data) {
        this.charOrder = data.charOrder;
        this.moveType = MoveType.byName(data.moveType);
        this.summonId = data.summonId ?? null;
        this.abilityCoolDowns = data.abilityCoolDowns || [];

        this.totalHitCount = data.totalHitCount;
        this.attackMultiHitCount = data.attackMultiHitCount;
        this.elementTypes = data.elementTypes || [];

        this.damages = data.damages || [];
        this.additionalDamages = data.additionalDamages || [];

        this.hps = data.hps || [];
        this.hpRates = data.hpRates || [];
        this.heals = data.heals || [];

        this.chargeGauges = data.chargeGauges || [];
        this.fatalChainGauge = data.fatalChainGauge ?? 0;

        // 스테이터스 매핑 [적][아군][아군][아군][아군]
        this.addedBattleStatusesList = (data.addedBattleStatusesList || []).map(
            statusList => statusList.map(s => new StatusDto(s))
        );
        this.addedBuffStatusesList = this.addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
        this.addedDebuffStatusesList = this.addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
        this.removedBattleStatusesList = (data.removedBattleStatusesList || []).map(
            statusList => statusList.map(s => new StatusDto(s))
        );
        this.removedBuffStatusesList = this.removedBattleStatusesList.map(removedBattleStatuses => removedBattleStatuses.filter(status => status.type === 'BUFF'));
        this.removedDebuffStatusesList = this.removedBattleStatusesList.map(removedDebuffStatuses => removedDebuffStatuses.filter(status => status.type === 'DEBUFF'));
        this.currentBattleStatusesList = (data.currentBattleStatusesList || []).map(
            statusList => statusList.map(s => new StatusDto(s))
        );

        this.enemyAttackTargetOrders = data.enemyAttackTargetOrders ?? null;
        this.allTarget = data.allTarget ?? false;

        this.omenType = OmenType.byName(data.omenType);
        this.omenValue = data.omenValue ?? null;
        this.omenCancelCondInfo = data.omenCancelCondInfo ?? null;
        this.omenName = data.omenName ?? null;
        this.omenInfo = data.omenInfo ?? null;

        this.enemyPowerUp = data.enemyPowerUp ?? false;
        this.enemyCtMax = data.enemyCtMax ?? false;
    }
}

// JSON 배열을 MoveResponse 인스턴스 배열로 변환하는 함수
function parseMoveResponseList(jsonArray) {
    return jsonArray.map(item => new MoveResponse(item));
}
