function createStateManager(initialState = {}) {
    const state = initialState;

    const subscribers = new Map(); // key: path, value: Set callback
    initRenderSubscribers();
    initOnSetSubscribers();

    /**
     * 상태 구독
     * @param path :String stage.gGameStatus 이하 path
     * @param callback
     * @return {(function(): void)|*} unsubscribe()
     */
    function subscribe(path, callback) {
        if (!subscribers.has(path)) {
            subscribers.set(path, new Set());
        }
        subscribers.get(path).add(callback);

        return () => {
            subscribers.get(path).delete(callback);
            if (subscribers.get(path).size === 0) {
                subscribers.delete(path); // path 없으면 삭제
            }
        };
    }

    /**
     * 상태 변경
     * @param {string} path stage.gGameStatus 이하 path
     * @param value 변경할 값
     * @param {Object} [options] 옵션
     * @param {boolean} [options.force] 같은 값 강제 업데이트 여부
     */
    function setState(path, value, options = {}) {
        const pathKeys = path.split(".");
        let target = state; // {... target: { lastKey: value }}

        for (let i = 0; i < pathKeys.length - 1; i++) {
            let key = pathKeys[i];
            if (!(key in target) || typeof target[key] !== "object" || target[key] === null) {
                // throw new Error(`[setState] key "${keys[i]}" does not exist.`); // 해당 필드 없으면 그냥 에러
                target[key] = {}; // 없으면 초기화 후 아래서 대입
            }
            target = target[key];
        }

        const lastKey = pathKeys[pathKeys.length - 1];
        const prevValue = target[lastKey];
        if (!options.force && _.isEqual(prevValue, value)) return; // 변경 없으면 실행 안함

        target[lastKey] = value;

        notifySubscribers(pathKeys, value, prevValue);
    }

    function notifySubscribers(pathKeys, value, prevValue) {
        subscribers.forEach((callbacks, subscribedPath) => {
            const subscribedPathKeys = subscribedPath.split('.');
            // 길이와 패턴이 정확히 일치하는지 확인 (abilities.* 구독중이면 abilities.5104 감지, abilities.5104.cooldown 은 무시)
            if (subscribedPathKeys.length !== pathKeys.length) return;

            let isMatch = true;
            const wildcardValues = []; // 실제 들어온 path.XXX 로 변경할 key array
            for (let i = 0; i < subscribedPathKeys.length; i++) {
                if (subscribedPathKeys[i] === '*') {
                    wildcardValues.push(pathKeys[i]);
                } else if (subscribedPathKeys[i] !== pathKeys[i]) {
                    isMatch = false;
                    break;
                }
            }
            if (!isMatch) return; // path 다르면 즉시 종료

            // 콜백 실행
            const params = wildcardValues.length > 0
                ? [...wildcardValues, value, prevValue] // wildCard 있을경우 첫번째 파라미터로 넘김
                : [value, prevValue];

            callbacks.forEach(callback => {
                try {
                    callback(...params);
                } catch (e) {
                    console.error("[setState] path = ", pathKeys.join('.'), " value = ", value, " Subscriber error: ", e);
                }
            });
        });
    }

    /**
     * 상태 조회
     * @param path :String stage.gGameStatus 이하 path
     * @return {{}|*}
     */
    function getState(path) {
        if (!path) return state;
        const keys = path.split(".");
        return keys.reduce((acc, key) => (acc ? acc[key] : undefined), state);
    }

    /**
     * 상태 삭제
     * @param path :String 삭제할 path
     */
    function deleteState(path) {
        if (!path) return;
        const pathKeys = path.split(".");
        let target = state;

        for (let i = 0; i < pathKeys.length - 1; i++) {
            const key = pathKeys[i];
            if (!(key in target) || typeof target[key] !== "object" || target[key] === null) {
                return; // 중간 경로 없음 -> 즉시 종료
            }
            target = target[key];
        }

        const lastKey = pathKeys[pathKeys.length - 1];
        if (!(lastKey in target)) return; // 이미 없는 키

        // const prevValue = target[lastKey];
        delete target[lastKey];

        // notifySubscribers(pathKeys, null, prevValue); // 삭제후 subscriber 갱신 (null 대신 별도의 empty value 필요?)
    }

    // renderer
    function initRenderSubscribers() {
        //battleCanvas
        // hp bar
        subscribe('hps', renderHp);
        subscribe('hpRates', renderHpRate);
        subscribe('enemyTriggerHps', renderEnemyTriggerHps);
        subscribe('isHalation', renderHalation);
        // guard
        subscribe('guardStates', renderGuards);
        // omen
        subscribe('omen', renderOmen);
        // indicator
        subscribe('indicator.moveName', renderMoveNameIndicator);
        subscribe('indicator.moveResultHonor', renderMoveResultHonorIndicator);
        subscribe('currentTurn', renderTurnIndicator);
        subscribe('remainingTimeString', renderRemainingTimeIndicator);
        // attackButton
        subscribe('isQuestCleared', renderAttackButton);
        subscribe('isQuestFailed', renderAttackButton);
        subscribe('isAttackClicked', renderAttackButton);

        //commandContainer
        // barrier, chargeGauge, fatalChainGauge
        subscribe('barriers', renderBarriers);
        subscribe('chargeGauges', renderChargeGauge);
        subscribe('enemyMaxChargeGauge', renderEnemyMaxChargeGauge);
        subscribe('fatalChainGauge', renderFatalChainGauge);
        // metadata
        subscribe('ability', renderAllAbilities);
        subscribe('supportAbility', renderAllSupportAbilities);
        subscribe('chargeAttack', renderAllChargeAttacks);
        subscribe('ability', renderAllAbilityIndicators);
        subscribe('ability.*', renderSingleAbility)
        // ability details
        subscribe('abilityCoolDowns', renderAbilityCoolDowns); // usableIndicator 통합
        subscribe('abilitySealeds', renderAbilityCoolDowns);
        // summon
        subscribe('summon', renderAllSummons);
        // summon details
        subscribe('leaderActorId', renderSummonButton);
        subscribe('summonCooldowns', renderSummonCooldowns);
        subscribe('usedSummon', renderSummonCooldowns);
        subscribe('unionSummonInfo', renderUnionSummonChance);
        // status
        subscribe('currentStatusEffectsList', renderCurrentStatusEffectsIcons);

        //memberInfoContainer
        subscribe('memberInfos', renderMemberInfoContainer)

        //chat
        subscribe('chatMessages', renderChatMessages);

        //modal
        // potion
        subscribe("potion.counts", renderPotionCount);
    }

    // onSet
    function initOnSetSubscribers() {
        // subscribe('omen', onOmenSet);
    }

    return {subscribe, setState, getState, deleteState};
}


async function initGameStatus() {
    // 로컬 스토리지 초기화
    if (localStorage.getItem('abilityStatusEffectInfoCheck') === null) localStorage.setItem('abilityStatusEffectInfoCheck', 'true');
    if (localStorage.getItem('abilityInfoCheck') === null) localStorage.setItem('abilityInfoCheck', 'true');
    if (localStorage.getItem('abilityInfoCheck') === 'true') {
        $('#showAbilityInfoCheck').prop('checked', true).trigger('change');
    }

    let roomId = $('#roomInfo').data('room-id');
    let initData = await fetch(`/api/rooms/${roomId}/members/me/battle-init`)
        .then(response => response.json())
        .catch(error => console.error('[initGameStatus] fetch error:', error));
    window.assetInfos = initData.assetInfos;

    let characterInfos = initData.characterInfo;
    let aliveCharacterOrders = initData.aliveCharacterOrders;
    let frontCharacterInfos = Object.fromEntries(Object.entries(characterInfos).filter(([order]) => aliveCharacterOrders.includes(Number(order))))
    // console.log(`[initGameStatus] aliveCharacterCounts = ${[...aliveCharacterOrders]} frontCharacterInfos = `, frontCharacterInfos);
    let enemyInfo = initData.enemyInfo;
    let fatalChainGauge = initData.fatalChainGauge;
    let fatalChainInfo = initData.fatalChainInfo;
    let leaderActorId = initData.leaderActorId; // nullable
    let summonInfos = initData.summonInfos;
    let triggerHps = initData.triggerHps;
    let changingMoves = initData.changingMoves;
    let allCharacterIds = initData.allCharacterIds;

    let isTutorial = !!initData.isTutorial;
    if (isTutorial) {
        // 튜토리얼 클릭 스로틀링
        (function ($) {
            const THROTTLE_MS = 200;
            const $container = $('#container');
            if (!$container.length) return;

            let isThrottled = false;

            const events = ['click', 'touchstart'];

            events.forEach(function (type) {
                $container[0].addEventListener(
                    type,
                    function (e) {
                        if ($(e.target).closest('.guard-button').length) return;

                        if (isThrottled) {
                            e.stopPropagation();
                            e.preventDefault();
                            return;
                        }

                        if (type === 'click') {
                            isThrottled = true;
                            setTimeout(function () {
                                isThrottled = false;
                            }, THROTTLE_MS);
                        }
                    },
                    true
                );
            });
        })(jQuery);
    }


    let lastMoveTime = initData.lastMoveTime ? new Date(initData.lastMoveTime) : null;
    let moveCooldown = initData.moveCooldown ?? 0;

    renderBattlePortraits(frontCharacterInfos);
    renderAbilityPanels(frontCharacterInfos);
    // render 직후 slick 초기화 (SSR 코드에서 그대로 이동)
    $('#abilitySlider .ability-panel').get().forEach(panel => {
        $(panel).find('.open-charge-attack-popover')
            .attr('data-actor-index', $(panel).attr('data-actor-order'));
    });
    $('#abilitySlider').slick({speed: 200, arrows: false});

    window.stage = {}
    stage.gGameStatus = {}
    stage.gGameStatus.raid_union_summon_name = 'hello';
    stage.gGameStatus.isQuestCleared = false;
    stage.gGameStatus.isQuestFailed = false;
    stage.gGameStatus.isQuestTimeout = false;
    stage.gGameStatus.isAttackClicked = false;

    stage.gGameStatus.lastMoveTime = lastMoveTime;
    stage.gGameStatus.moveCooldown = moveCooldown;

    // 그랑블루 사양에 따른 일부 변수추가할당
    stage.global = {};
    stage.global.is_pair_chain = false; // 보이스 재생용인듯. 잇는캐릭 잇고 없는 캐릭 잇어서 비활성화
    stage.pJsnData = {}; // quest_clear.js

    // 프론트에서만 임시로 관리되는 상태
    stage.gGameStatus.doUnionSummon = true; // 합체소환 여부
    stage.gGameStatus.usedSummon = initData.usedSummon; // 초기 로드시만 서버갱신
    stage.gGameStatus.currentTurn = initData.currentTurn;
    stage.gGameStatus.startTime = new Date(initData.startTime); // createManager 후 초당 인터벌 갱신 하나달아줌

    // 기본 상태
    stage.gGameStatus.leaderActorId = leaderActorId;
    stage.gGameStatus.actorIds = [initData.enemyInfo.id, null, null, null, null];
    Object.values(frontCharacterInfos).forEach(characterInfo => {
        stage.gGameStatus.actorIds[Number(characterInfo.order)] = characterInfo.id;
    })
    stage.gGameStatus.allCharacterIds = allCharacterIds; // 사망 포함 전원 캐릭터 id

    // 어빌리티, 소환석, 서포트어빌리티, 오의, 페이탈체인 메타데이터 등록
    stage.gGameStatus.ability = {};
    stage.gGameStatus.supportAbility = {}; // 서포트 어빌리티는 actorId : { [moveInfo, moveInfo, ...] }
    stage.gGameStatus.summon = {};
    stage.gGameStatus.chargeAttack = {};
    let allMoves = [
        ...Object.values(characterInfos).flatMap(characterInfo => characterInfo.abilities),
        ...Object.values(characterInfos).flatMap(characterInfo => characterInfo.supportAbilities),
        ...Object.values(characterInfos).map(characterInfo => characterInfo.chargeAttack),
        fatalChainInfo,
        ...summonInfos,
    ]
    stage.gGameStatus.popoverMap = new Map(); // 팝 오버용 맵

    Object.entries(characterInfos).forEach(([currentOrder, characterInfo]) => {

        allMoves.forEach((move, index) => {
            let moveInfo = new MoveInfo(move);
            switch (move.type) {
                case 'ABILITY':
                    stage.gGameStatus.ability[move.id] = moveInfo;
                    // stage.gGameStatus.abilityByActor[move.actorIndex][index] = moveInfo;
                    break;
                case 'SUPPORT_ABILITY':
                    if (!stage.gGameStatus.supportAbility[move.actorId]) stage.gGameStatus.supportAbility[move.actorId] = [];
                    stage.gGameStatus.supportAbility[move.actorId][move.order - 1] = moveInfo;
                    break;
                case 'CHARGE_ATTACK':
                    stage.gGameStatus.chargeAttack[move.id] = moveInfo;
                    break;
                case 'SUMMON':
                    stage.gGameStatus.summon[move.id] = moveInfo;
                    break;
                case 'FATAL_CHAIN_DEFAULT':
                    stage.gGameStatus.fatalChain = moveInfo;
                    stage.gGameStatus.ability[move.id] = moveInfo;
                    break;
            }
        });
    });

    // 변화 Move 별도등록
    stage.gGameStatus.changingMove = {};
    changingMoves.forEach(changingMove => {
        let moveInfo = new MoveInfo(changingMove);
        stage.gGameStatus.changingMove[changingMove.id] = moveInfo;
    });

    // 공격
    stage.gGameStatus.attack = new MoveInfo({
        type: 'ATTACK',
        name: '공격',
        iconImageSrc: '/static/assets/img/ui/ui-attack-icon.png'
    });
    // 포션
    stage.gGameStatus.potion = {POTION: {}, ALL_POTION: {}, ELIXIR: {}};
    stage.gGameStatus.potion.POTION = new MoveInfo({
        type: 'POTION',
        potionType: 'POTION',
        iconImageSrc: '/static/assets/img/ui/potion.jpg',
        //actorId 가 나중에 타겟으로 들어감
    });
    stage.gGameStatus.potion.ALL_POTION = new MoveInfo({
        type: 'POTION',
        potionType: 'ALL_POTION',
        iconImageSrc: '/static/assets/img/ui/all-potion.jpg',
    });
    stage.gGameStatus.potion.ELIXIR = new MoveInfo({
        type: 'POTION',
        potionType: 'ELIXIR',
        iconImageSrc: '/static/assets/img/ui/elixir.jpg',
    });
    let potionCounts = $('#potionModal .potion-icon-container .count').map((index, element) => element.textContent).toArray();
    stage.gGameStatus.potion.counts = potionCounts;

    // 특수 상태
    stage.gGameStatus.enemyActorName = enemyInfo.name; // 첫 로드, 폼체인지 시 갱신
    stage.gGameStatus.enemyFormOrder = enemyInfo.formOrder;
    stage.gGameStatus.isFatalDamagedVoice = [false, false, false, false, false]; // 대 데미지 피격 / 피격으로 인한 빈사상태 발생시 true
    stage.gGameStatus.isHalation = [false, false, false, false, false]; // 할레이션
    stage.gGameStatus.isTutorial = isTutorial;

    // 인디케이터
    stage.gGameStatus.indicator = {}
    stage.gGameStatus.indicator.moveName = '';
    stage.gGameStatus.indicator.moveResultHonor = 0;

    // 채팅
    stage.gGameStatus.lastChatId = null;
    stage.gGameStatus.chatMessages = [];

    // syncResponse 로 초기화
    stage.gGameStatus.barriers = [null, null, null, null, null];
    stage.gGameStatus.abilityCoolDowns = [];
    stage.gGameStatus.abilityUsables = [];
    stage.gGameStatus.doubleAttackRates = [];
    stage.gGameStatus.tripleAttackRates = [];
    stage.gGameStatus.currentStatusEffectsList = {};
    stage.gGameStatus.enemyMaxChargeGauge = 0;
    stage.gGameStatus.omen = OmenDto.empty();
    stage.gGameStatus.guardStates = [];
    stage.gGameStatus.summonCooldowns = []; // 편의를 위해 별도로 저장
    stage.gGameStatus.unionSummonInfo = null; // 합체소환시 이 값을 프론트에서 그대로 쓰기 위해 상태 갱신시 한꺼번에 하지 말것. SYNC 에서만 갱신할것.
    stage.gGameStatus.canChargeAttacks = [false, false, false, false, false];

    // gameStateManager 생성 & response 사용 시작 =========================================================================
    window.gameStateManager = createStateManager(stage.gGameStatus);

    // SYNC 요청 및 응답 반환
    let response = await requestSync(true);
    // console.log('[initGameStatus] response = ', response)
    let syncResponses = response.syncResponses
    let syncResponseJson = syncResponses[0];
    let syncResponse = parseMoveResponseList([syncResponseJson])[0];
    triggerHps = response.triggerHps && response.triggerHps.length > 0 ? response.triggerHps : triggerHps;
    window.stage.response = {};
    window.stage.response.processing = syncResponse;

    window.stage.response.scheduled = [];
    if (syncResponses.length > 1) {
        // 전투시작 처리 예약
        let scheduledResponsesJson = syncResponses.slice(1);
        window.stage.response.scheduled = scheduledResponsesJson; // 처리시 해당 함수에서 parse 진행

    }
    // console.log('[initGameStatus] syncResponse = ', syncResponse);

    // 첫 로드로 렌더링이 튀는걸 방지하기 위해 게이지 관련 요소들은 SSR 로 렌더링됨
    stage.gGameStatus.hps = syncResponse.hps;
    stage.gGameStatus.fatalChainGauge = syncResponse.fatalChainGauge;

    // 적 체력바는 레이어 반영을 위해 force update
    stage.gGameStatus.isLayeredHpBar = false; // TODO 조건추가
    gameStateManager.setState('hpRates', syncResponse.hpRates);

    // 요소 초기 렌더링 (어빌리티, 소환석)
    gameStateManager.setState('ability', gameStateManager.getState('ability'), {force: true});
    gameStateManager.setState('summon', gameStateManager.getState('summon'), {force: true});
    gameStateManager.setState('supportAbility', gameStateManager.getState('supportAbility'), {force: true});
    gameStateManager.setState('chargeAttack', gameStateManager.getState('chargeAttack'), {force: true});

    //전조, 차지턴
    gameStateManager.setState('enemyMaxChargeGauge', syncResponse.enemyMaxChargeGauge);
    gameStateManager.setState('chargeGauges', syncResponse.chargeGauges, {force: true}); // 적의 차지턴은 렌더링 해줘야됨
    gameStateManager.setState('omen', syncResponse.omen);
    gameStateManager.setState('enemyTriggerHps', triggerHps); // hpRate, omen 필요

    // 가드
    let initialGuardStates = $('#actorContainer .guard-status').toArray().map(element => element.dataset.initialGuardState === 'true');
    initialGuardStates.unshift(null); // 적 null
    setTimeout(() => gameStateManager.setState('guardStates', initialGuardStates), 1000); // 살짝 딜레이

    // 주인공 및 주인공 종속 상태
    let isLeaderAlive = !!gameStateManager.getState('actorIds').find(actorId => actorId === leaderActorId);
    if (!isLeaderAlive) {
        gameStateManager.setState('leaderActorId', null, {force: true});
    } else {
        // 리더 사망시 아래는 렌더링 하지 않음
        gameStateManager.setState('summonCooldowns', syncResponse.summonCooldowns);
        gameStateManager.setState('unionSummonInfo', syncResponse.unionSummonInfo);
    }

    // 기타 상태
    gameStateManager.setState('barriers', syncResponse.barriers);
    gameStateManager.setState('canChargeAttacks', syncResponse.canChargeAttacks);
    gameStateManager.setState('abilitySealeds', syncResponse.abilitySealeds); // 첫 로드는 쿨다운 보다 먼저해야 usableIndicator 에서 제대로 렌더링 가능
    gameStateManager.setState('abilityCoolDowns', syncResponse.abilityCoolDowns);
    gameStateManager.setState('doubleAttackRates', syncResponse.doubleAttackRates);
    gameStateManager.setState('tripleAttackRates', syncResponse.tripleAttackRates);

    gameStateManager.setState('currentStatusEffectsList', syncResponse.currentStatusEffectsList);
    // 현재 상태효과에 따른 추가처리
    let isHalation = [false, false, false, false, false];
    syncResponse.currentStatusEffectsList.forEach((currentEffects, actorIndex) => {
        if (actorIndex === 0) return;
        currentEffects.forEach(statusEffect => {
            if (statusEffect.name === '하레이션') {
                // 하레이션
                isHalation[actorIndex] = true;
            }
        })
    })
    gameStateManager.setState('isHalation', isHalation);

    gameStateManager.setState('enemyEstimatedAtk', syncResponse.enemyEstimatedAtk);

    // 멤버정보 로드
    requestMembersInfo();
    // 채팅 로드
    stage.gGameStatus.chatMessages = null; // 렌더링 하지 않고 미리 초기화 (init 구분)
    requestChat();

    // 시간 갱신용 인터벌
    const battleDuration = 45;
    window.startTimeIntervalId = window.setInterval(() => {
        let startTime = gameStateManager.getState('startTime'); // Date
        const elapsedMs = Date.now() - startTime; // (ms)
        const remainingMs = (battleDuration * 60 * 1000) - elapsedMs; // (ms)
        if (remainingMs <= 0) {
            clearInterval(window.startTimeIntervalId);
            stopSync();
            doSync(true);
            gameStateManager.setState('isQuestTimeout', true);
            return;
        }

        const remainingSeconds = Math.ceil(remainingMs / 1000);
        const minutePart = Math.floor(remainingSeconds / 60);
        const secondPart = remainingSeconds % 60;
        let formattedRemainingTime = `${minutePart.toString().padStart(2, '0')}:${secondPart.toString().padStart(2, '0')}`;
        gameStateManager.setState('remainingTimeString', formattedRemainingTime);
    }, 1000);

}

// 1. 루프 담당 - characterInfos 전체를 받아 순회
function renderBattlePortraits(characterInfos) {
    const $wrapper = $('.battle-member-wrapper');
    $wrapper.empty();

    // 빈 슬롯 먼저 4개 채우기
    for (let order = 1; order <= 4; order++) {
        $wrapper.append(`
            <div class="battle-portrait empty">
                <img src="/static/assets/img/gl/ch-empty.jpg" data-seq="${order}">
            </div>
        `);
    }

    // characterInfo 있는 슬롯만 교체
    Object.values(characterInfos).forEach(info => {
        renderBattlePortrait(info);
    });
}

// 2. 단일 렌더링 + 삽입 담당 - info.order - 1 위치의 슬롯과 교체
function renderBattlePortrait(info) {
    const hpDangerClass = info.hpRate <= 25 ? 'bg-danger' : '';
    const additionalHiddenClass = info.maxChargeGauge > 100 ? '' : 'hidden';

    const $portrait = $(`
        <div class="battle-portrait actor-${info.order}"
             data-actor-order="${info.order}"
             data-actor-id="${info.id}">
            <img src="${info.portraitSrc}">
            <div class="status-container actor-${info.order}"></div>
            <div class="hp-gauge-wrapper">
                <div class="hp-gauge">
                    <div class="barrier-value"><span class="value"></span></div>
                    <div class="hp-gauge-value"><span class="value">${info.hp}</span></div>
                    <div class="progress" role="progressbar">
                        <div class="progress-bar bg-gradient ${hpDangerClass}"
                             data-hp-rate="${info.hpRate}"
                             style="width: ${info.hpRate}%"></div>
                    </div>
                </div>
            </div>
            <div class="charge-gauge-wrapper">
                <div class="charge-gauge">
                    <div class="progress" role="progressbar">
                        <div class="progress-bar" style="width: ${info.chargeGauge}%"></div>
                    </div>
                    <div class="progress additional ${additionalHiddenClass}" role="progressbar">
                        <div class="progress-bar" style="width: ${info.chargeGauge}%"></div>
                    </div>
                    <div class="charge-gauge-value"><span class="value">${info.chargeGauge}</span>%</div>
                </div>
            </div>
            <div class="ability-usable-indicator-wrapper">
                <div class="ability-usable-indicator"></div>
                <div class="ability-usable-indicator"></div>
                <div class="ability-usable-indicator"></div>
                <div class="ability-usable-indicator"></div>
            </div>
        </div>
    `);

    $('.battle-member-wrapper .battle-portrait').eq(info.order - 1).replaceWith($portrait);
}

// 어빌리티 슬라이더 내부의 패널 전체 초기 로드
function renderAbilityPanels(characterInfos) {
    const $slider = $('#abilitySlider');
    $slider.empty();

    Object.values(characterInfos)
        .sort((a, b) => a.order - b.order)
        .forEach(info => $slider.append(createAbilityPanel(info)));
}

// 어빌리티 슬라이더 내부의 패널 하나 생성후 삽입 (동적추가, 부활 등)
function renderAbilityPanel(characterInfo) {
    const $slider = $('#abilitySlider');
    const $slickSlides = $slider.find('.slick-slide:not(.slick-cloned)');

    // slick 으로 삽입시 slick-slide index 구하기 (내부의 panel.actor-order 기준으로 slide 의 index 반환)
    const insertBeforeIndex = $slickSlides.toArray().findIndex(slide => parseInt($(slide).find('.ability-panel').data('actor-order')) > characterInfo.order);
    const $panel = createAbilityPanel(characterInfo);

    if (insertBeforeIndex === -1) {
        $slider.slick('slickAdd', $panel);
    } else {
        $slider.slick('slickAdd', $panel, insertBeforeIndex, true);
    }
}

// 어빌리티 슬라이너 내부 패널 생성 - $element 반환
function createAbilityPanel(info) {
    return $(`
        <div class="slick-slider-item">
            <div class="ability-panel actor-${info.order}" data-actor-order="${info.order}">

                <div class="ability-battle-portrait">
                    <img src="${info.portraitSrc}">
                    <div class="hp-gauge-wrapper">
                        <div class="hp-gauge">
                            <div class="barrier-value"><span class="value"></span></div>
                            <div class="hp-gauge-value"><span class="value">${info.hp}</span></div>
                            <div class="progress" role="progressbar">
                                <div class="progress-bar bg-gradient ${info.hpRate <= 25 ? 'bg-danger' : ''}"
                                     style="width: ${info.hpRate}%"></div>
                            </div>
                        </div>
                    </div>
                    <div class="charge-gauge-wrapper">
                        <div class="charge-gauge">
                            <div class="progress" role="progressbar">
                                <div class="progress-bar" style="width: ${info.chargeGauge}%"></div>
                            </div>
                            <div class="progress additional ${info.maxChargeGauge > 100 ? '' : 'hidden'}" role="progressbar">
                                <div class="progress-bar" style="width: ${info.chargeGauge}%"></div>
                            </div>
                            <div class="charge-gauge-value"><span class="value">${info.chargeGauge}</span>%</div>
                        </div>
                    </div>
                </div>

                <div class="panel-wrapper">
                    <div class="other-move-wrapper">
                        <div class="charge-attack-wrapper"></div>
                        <div class="support-ability-wrapper"></div>
                    </div>
                    <div class="ability-wrapper"></div>
                </div>

                <div class="status-container party actor-${info.order}" data-actor-index="${info.order}">
                    <div class="show-status-info-button-wrapper">
                        <button class="btn btn-secondary btn-xxsm show-status-info-button">상태 정보</button>
                    </div>
                </div>

            </div>
        </div>
    `);
}