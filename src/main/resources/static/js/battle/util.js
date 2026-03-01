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
function playAdditionalSe(cjsName = null, motion = null, moveType = null) {
    console.debug('[playAdditionalSound] actorName = ', cjsName, ' moveType = ', moveType, ' motion = ', motion);
    if (!Sounds[cjsName]) return;
    let additionalSound = Sounds[cjsName].additional;
    if (!additionalSound) return;

    let soundByMotion = additionalSound[motion]?.src;
    let soundByMoveType = additionalSound[moveType?.name]?.src;
    let soundByMoveTypeParent = additionalSound[moveType?.getParentType()?.name]?.src;

    console.log('[playAdditionalSound] soundByMotion = ', soundByMotion, ' soundByMoveType = ', soundByMoveType, ' soundByMoveTypeParent = ', soundByMoveTypeParent);
    [soundByMotion, soundByMoveType, soundByMoveTypeParent].forEach(src => window.audio.play(src, {isLocal: true}));
}

function updateBgm(response, {stopBgm = false} = {}) {
    if (stopBgm === true) window.audio.removeBgm(window.audio.bgmSrc);
    let enemyCjses = gameStateManager.getState('enemyMainCjsNames');
    let currentEnemyCjs = enemyCjses[0];
    // omen.standbyMoveType
    let standbyMoveType = gameStateManager.getState('omen.standbyMoveType');
    let standbyBgm = !!standbyMoveType ? Sounds[currentEnemyCjs].bgm[standbyMoveType.name] : null;
    // moveType
    let responseMoveType = response.moveType;
    let moveTypeBgm = Sounds[currentEnemyCjs].bgm[responseMoveType.name];
    // hp
    let hpKey = Object.keys(Sounds[currentEnemyCjs].bgm).map(Number).sort((a, b) => a - b).find(k => k >= response.hpRates[0]);
    let hpBgm = Sounds[currentEnemyCjs].bgm[hpKey];
    // current
    let currentBgm = gameStateManager.getState('bgm') || {index: 0, formOrder: 0, src: ''};
    let bgmCandidates = [standbyBgm, moveTypeBgm, hpBgm, currentBgm].filter(bgm => bgm);

    let nextBgm = bgmCandidates
        .sort((a, b) => (a.formOrder - b.formOrder) || (a.index - b.index))[bgmCandidates.length - 1]; // formOrder 를 우선비교
    console.debug('[updateBgm] standbyBgm = ', standbyBgm, ' moveTypeBgm = ', moveTypeBgm, ' hpBgm = ', hpBgm, ' currentBgm = ', currentBgm, ' nextBgm = ', nextBgm);
    if (nextBgm.src !== currentBgm.src) {
        gameStateManager.setState('bgm', nextBgm);
        window.audio.playBgm(nextBgm.src);
    }
}


/**
 *  sync 인터벌
 * @return {number} timerId
 */
function doSync() {
    if (!window.syncTimerId) { // 없을때만 등록
        requestSync();
        window.syncTimerId = window.setInterval(requestSync, 10000);
    }
}

function stopSync() {
    console.log('[stopSync] timerId = ', window.syncTimerId);
    window.clearInterval(window.syncTimerId);
    window.syncTimerId = null;
}