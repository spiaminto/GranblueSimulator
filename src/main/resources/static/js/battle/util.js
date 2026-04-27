/**
 * 공통 대기 함수
 * @param ms 대기시간
 * @return {Promise<number>} 대기시간
 */
const wait = (ms) => new Promise(resolve => setTimeout(() => resolve(ms), ms));


/**
 * 데미지 요소를 만들어반환
 * 일반적으로, 해당 데미지요소는 XXDamgeWrapper 에 append 됨.
 * 난격을 포함하는 캐릭터 일반공격은 대응하지 않음.
 * @param actorOrder
 * @param elementType
 * @param moveType 행동 타입, 'attack', 'ability', chargeAttack'
 * @param damageType 데미지 타입 'NORMAL', 'ADVANTAGE', ....
 * @param index 데미지 인덱스
 * @param damage
 * @param additionalDamages
 * @param isEnemyDamage optional boolean
 * @returns {{$damage: (*|jQuery), $additionalDamage: (*|jQuery)}}
 */
function getDamageElement(actorOrder, elementType, moveType, damageType, index, damage, additionalDamages, isEnemyDamage = false) {
    let moveTypeClassName;
    switch (moveType) {
        case 'attack':
            moveTypeClassName = ' attack-damage';
            break;
        case 'ability':
            moveTypeClassName = ' ability-damage';
            break;
        case 'charge-attack':
            moveTypeClassName = ' charge-attack-damage';
            break;
        default:
            new Error('[getDamageElement] invalid type, type = ' + moveType);
    }

    let damageTypeClassname = ' ' + damageType?.toLowerCase();
    let enemyClassName = isEnemyDamage ? ' enemy' : '';
    let missClassName = damage === 'MISS' ? ' damage-miss' : '';

    // 공격
    let $damage = $(`
        <div class="damage actor-${actorOrder} element-type-${elementType.toLowerCase()} damage-index-${index} ${moveTypeClassName} ${damageTypeClassname} ${enemyClassName} ${missClassName}">
          ${damage}
        </div>
    `);

    // 추격
    let $additionalDamage = $(`
        <div class="additional-damage-wrapper actor-${actorOrder} element-type-${elementType.toLowerCase()} ${damageTypeClassname} ${missClassName} ${enemyClassName}">
          ${damage}
        </div>    
    `).append((additionalDamages || []).map(additionalDamage =>  // 추격이 존재하면 붙임
        $(`
            <div class="additional-damage element-type-${elementType.toLowerCase()} ${enemyClassName}">
              ${additionalDamage}
            </div>  
        `)
    ));

    return {$damage, $additionalDamage};
}


function playSe(src) {
    window.audio.play(src, {isLocal: true});
}

/**
 * 추가 사운드를 재생
 */
function playAdditionalSe(cjsName = null, motion = null) {
    // console.debug('[playAdditionalSe] actorName = ', cjsName, ' motion = ', motion);
    let sfxByCjsName = Sounds.SFX[cjsName];
    if (!cjsName || !sfxByCjsName) return;

    let key = motion ? motion : 'default';
    let sfxObjs = sfxByCjsName[key];
    // console.log(`[playAdditionalSe] sfxObjs = ${sfxObjs ? [...sfxObjs] : 'null'}`)
    if (!sfxObjs) return;

    sfxObjs.forEach(sfxObj => {
        sfxObj.delay > 0
            ? setTimeout(() => window.audio.play(sfxObj.src, {isLocal: true}), sfxObj.delay)
            : window.audio.play(sfxObj.src, {isLocal: true});
    });
}

function updateBgm(response, {stopBgm = false} = {}) {
    if (stopBgm === true) {
        window.audio.removeBgm();
        return;
    }

    let currentEnemyCjs = gameStateManager.getState('enemyMainCjsNames')[0];
    let bgmByCjsName = Sounds.BGM[currentEnemyCjs];
    if (!bgmByCjsName) return;

    // omen.standbyMoveType
    let standbyMoveType = gameStateManager.getState('omen.standbyMoveType')?.name;
    let standbyBgm = standbyMoveType ? bgmByCjsName[standbyMoveType] : null;
    // moveType
    let responseMoveType = response.moveType;
    let moveTypeBgm = bgmByCjsName[responseMoveType.name];
    // hp
    let hpKey = Object.keys(bgmByCjsName).map(Number).sort((a, b) => a - b).find(k => k >= response.hpRates[0]);
    let hpBgm = bgmByCjsName[hpKey];
    // current
    let currentBgm = gameStateManager.getState('bgm') || {index: 0, formOrder: 0, src: ''};
    let bgmCandidates = [standbyBgm, moveTypeBgm, hpBgm, currentBgm].filter(bgm => bgm);

    let nextBgm = bgmCandidates
        .sort((a, b) => (a.formOrder - b.formOrder) || (a.index - b.index))[bgmCandidates.length - 1]; // formOrder 를 우선비교

    if (Object.values(bgmByCjsName).filter(value => value.src === nextBgm.src).length === 0) {
        // 재생예정인 bgm 이 현재 적의 cjs 와 맞지않음 -> 해당 적의 첫 bgm 으로 fallback
        nextBgm = Object.values(bgmByCjsName)[0];
    }

    // console.debug('[updateBgm] standbyBgm = ', standbyBgm, ' moveTypeBgm = ', moveTypeBgm, ' hpBgm = ', hpBgm, ' currentBgm = ', currentBgm, ' nextBgm = ', nextBgm);
    if (nextBgm.src !== currentBgm.src) {
        gameStateManager.setState('bgm', nextBgm);
        window.audio.playBgm(nextBgm.src, nextBgm.startOffset || 0);
    }
}


/**
 *  sync 인터벌
 *  @param withImmediateRequest 첫요청 즉시 실형여부, true 시 즉시실행
 * @return {number} timerId
 */
function doSync(withImmediateRequest = false) {
    if (withImmediateRequest) {
        // 즉시 실행시, 이전의 타이머를 제거하여 인터벌을 초기화함.
        stopSync();
        requestSync();
    }

    if (!window.syncTimerId) {  // 타이머 없을 때만 등록
        const intervalTime = gameStateManager.getState('memberInfos')?.length > 1 ? 4000 : 8000;
        window.syncTimerId = window.setInterval(requestSync, intervalTime);
    }
}

function stopSync() {
    // console.debug('[stopSync] timerId = ', window.syncTimerId);
    window.clearInterval(window.syncTimerId);
    window.syncTimerId = null;
}