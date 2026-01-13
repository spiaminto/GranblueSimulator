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
    function setState(path, value, options= {}) {
        const keys = path.split(".");
        let target = state; // {... target: { lastKey: value }}

        for (let i = 0; i < keys.length - 1; i++) {
            let key = keys[i];
            if (!(key in target || typeof target[key] !== "object" || target[key] === null)) {
                // throw new Error(`[setState] key "${keys[i]}" does not exist.`); // 해당 필드 없으면 그냥 에러
                target[key] = {}; // 없으면 초기화 후 아래서 대입
            }
            target = target[key];
        }

        const lastKey = keys[keys.length - 1];
        const prevValue = target[lastKey];
        if (!options.force && _.isEqual(prevValue, value)) return; // 변경 없으면 실행 안함

        target[lastKey] = value;

        // path 구독중인 subscriber 실행
        target[lastKey] = value;
        if (subscribers.has(path)) {
            subscribers.get(path).forEach(callback => {
                try {
                    callback(value, prevValue);
                } catch (e) {
                    console.error("[setState] Subscriber error:", e);
                }
            });
        }
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

    // renderer
    function initRenderSubscribers() {
        //battleCanvas
        // hp bar
        subscribe('hps', renderHp);
        subscribe('hpRates', renderHpRate);
        subscribe('enemyTriggerHps', renderEnemyTriggerHps);
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
        // chargeGauge, fatalChainGauge
        subscribe('chargeGauges', renderChargeGauge);
        subscribe('enemyMaxChargeGauge', renderEnemyMaxChargeGauge);
        subscribe('fatalChainGauge', renderFatalChainGauge);
        // ability
        subscribe('abilityCoolDowns', renderAbilityCoolDowns);
        subscribe('abilityCoolDowns', renderAbilityUsableIndicator);
        subscribe('abilitySealeds', renderAbilitySealeds);
        // summon
        subscribe('leaderActorId', renderSummonButton);
        subscribe('summonCooldowns', renderSummonCooldowns);
        subscribe('unionSummonId', renderUnionSummonChance);
        // status
        subscribe('currentStatusEffectsList', renderCurrentStatusEffectsIcons);

        //memberInfoContainer
        subscribe('memberInfos', renderMemberInfoContainer)

        //modal
        // potion
        subscribe("potion.counts", renderPotionCount);
    }

    // onSet
    function initOnSetSubscribers() {
        subscribe('omen', onOmenSet);
    }

    return {subscribe, setState, getState};
}

function onOmenSet(newVal, oldVal) {
    console.log('[onOmenSet] newVal = ', newVal, ' oldVal = ', oldVal);
    let omen = newVal;
    if (omen.type) {
        // 전조 모션 재생
        window.player && player.play(Player.playRequest('actor-0', gameStateManager.getState('omen.motion')));
    }
}

function onBgmIndexSet(newVal, oldVal) {
    console.log('[onBgmIndexSet] newVal = ', newVal, ' oldVal = ', oldVal);
    let bgmIndex = newVal;
}


async function initGameStatus() {

    window.stage = {}
    stage.gGameStatus = {}
    stage.gGameStatus.raid_union_summon_name = 'hello';
    stage.gGameStatus.isQuestCleared = false;
    stage.gGameStatus.isAttackClicked = false;

    // 그랑블루 사양에 따른 일부 변수추가할당
    // 페이탈체인 용
    stage.global = {};
    stage.global.is_pair_chain = false; // 보이스 재생용인듯. 잇는캐릭 잇고 없는 캐릭 잇어서 비활성화
    stage.pJsnData = {}; // quest_clear.js

    // 프론트에서만 임시로 관리되는 상태
    stage.gGameStatus.doUnionSummon = true; // 합체소환 여부
    stage.gGameStatus.currentTurn = Number($('.top-menu-container .turn-indicator .value').text()); // 현재 턴
    stage.gGameStatus.startTime = new Date($('#roomInfo').attr('data-room-created-at')); // createManager 후 초당 인터벌 갱신 하나달아줌

    // 기본 상태
    let $fatalChainGaugeWrapper = $('.advanced-command-container .fatal-chain-gauge-wrapper');
    stage.gGameStatus.leaderActorId = $fatalChainGaugeWrapper.attr('data-actor-id');
    stage.gGameStatus.actorIds = []; // actorIds[actorOrder]
    let $battlePortraits = $('.battle-portrait');
    _.range(0, 4).forEach(index => stage.gGameStatus.actorIds[$battlePortraits.eq(index).attr('data-actor-order')] = $battlePortraits.eq(index).attr('data-actor-id'));
    stage.gGameStatus.actorIds[0] = ($('.enemy-info-container').attr('data-initial-actor-id'));
    stage.gGameStatus.enemyActorName = $('.enemy-info-container').attr('data-initial-actor-name'); // 첫 로드, 폼체인지 시 갱신
    stage.gGameStatus.enemyFormOrder = Number($('.enemy-info-container').attr('data-initial-form-order'));

    // 인디케이터
    stage.gGameStatus.indicator = {}
    stage.gGameStatus.indicator.moveName = '';
    stage.gGameStatus.indicator.moveResultHonor = 0;

    // syncResponse 로 초기화
    stage.gGameStatus.abilityCoolDowns = [];
    stage.gGameStatus.abilityUsables = [];
    stage.gGameStatus.currentStatusEffectsList = {};
    stage.gGameStatus.enemyMaxChargeGauge = 0;
    stage.gGameStatus.omen = {};
    stage.gGameStatus.currentMotions = [];
    stage.gGameStatus.guardStates = [];
    stage.gGameStatus.summonCooldowns = []; // 편의를 위해 별도로 저장
    stage.gGameStatus.unionSummonId = null; // 상태 갱신시 한꺼번에 하지 말것. SYNC 에서만 갱신할것. 합체소환시 이 값을 프론트에서 그대로 쓰기 위해.

    // move 할당
    stage.gGameStatus.abilityByActor = {1: [], 2: [], 3: [], 4: []};
    stage.gGameStatus.ability = {};
    // 어빌리티
    window.abilityPanels.forEach((element, index) => {
        let $abilityIcons = $(element).find('.ability-icon');
        $abilityIcons.each((index, abilityIcon) => {
            let $abilityIcon = $(abilityIcon);
            let abilityId = $abilityIcon.attr('data-move-id');
            let actorIndex = $abilityIcon.attr('data-actor-index');
            let ability =
                new MoveInfo({
                    type: 'ABILITY',
                    id: abilityId,
                    name: $abilityIcon.attr('data-name'),
                    order: $abilityIcon.attr('data-order'),
                    actorId: $abilityIcon.attr('data-actor-id'),
                    actorIndex: actorIndex,
                    info: $abilityIcon.attr('data-info'),
                    coolDown: $abilityIcon.attr('data-cooldown'),
                    usable: $abilityIcon.attr('data-usable') === 'true',
                    iconSrc: $abilityIcon.find('img').attr('src'),
                    additionalType: $abilityIcon.attr('data-ability-type'),
                });
            stage.gGameStatus.ability[abilityId] = ability;
            stage.gGameStatus.abilityByActor[actorIndex][index] = ability;
        })
    })

    // 소환석
    stage.gGameStatus.summon = {};
    let $summons = $('.summon-display-wrapper .summon-list-item:not(.empty)');
    $summons.each((index, summon) => {
        let $summon = $(summon);
        let summonId = $summon.attr('data-move-id');
        stage.gGameStatus.summon[summonId] = new MoveInfo({
            type: 'SUMMON',
            id: summonId,
            name: $summon.attr('data-name'),
            order: index + 1,
            actorId: stage.gGameStatus.leaderActorId,
            info: $summon.attr('data-info'),
            coolDown: $summon.attr('data-cooldown'), // 초기만 로딩
            usable: $summon.attr('data-usable') === 'true',
            iconSrc: $summon.attr('data-icon-image-src'),
            portraitSrc: $summon.find('img').attr('src')
        })
    })

    // 공격
    stage.gGameStatus.attack = new MoveInfo({
        type: 'ATTACK',
        name: '공격',
        iconSrc: '/static/assets/img/ui/ui-attack-icon.png'
    })

    // 페이탈 체인
    let fatalChain = new MoveInfo({
        type: 'FATAL_CHAIN',
        id: $fatalChainGaugeWrapper.attr('data-move-id'),
        actorId: stage.gGameStatus.leaderActorId,
        name: $fatalChainGaugeWrapper.attr('data-name'),
        info: $fatalChainGaugeWrapper.attr('data-info'),
        iconSrc: $fatalChainGaugeWrapper.attr('data-icon-image-src')
    })
    stage.gGameStatus.fatalChain = fatalChain;
    stage.gGameStatus.ability[fatalChain.id] = fatalChain;

    // 포션
    stage.gGameStatus.potion = {allPotion: {}, potion: {}, elixir: {}};
    stage.gGameStatus.potion.single = new MoveInfo({
        type: 'POTION',
        additionalType: 'single',
        iconSrc: '/static/assets/img/ui/potion.jpg',
        //actorId 가 나중에 타겟으로 들어감
    })
    stage.gGameStatus.potion.all = new MoveInfo({
        type: 'POTION',
        additionalType: 'all',
        iconSrc: '/static/assets/img/ui/all-potion.jpg',
    })
    stage.gGameStatus.potion.elixir = new MoveInfo({
        type: 'POTION',
        additionalType: 'elixir',
        iconSrc: '/static/assets/img/ui/elixir.jpg',
    })
    let potionCounts = $('#potionModal .potion-icon-container .count').map((index, element) => element.textContent).toArray();
    stage.gGameStatus.potion.counts = potionCounts;

    // gameStateManager 생성 & response 사용 시작 ===============================================================
    window.gameStateManager = createStateManager(stage.gGameStatus);
    
    // 시간 갱신용 인터벌
    const battleDuration = 30; // (m), 30분 고정
    window.startTimeIntervalId = window.setInterval(() => {
        let startTime = gameStateManager.getState('startTime'); // Date
        const elapsedMs = Date.now() - startTime; // (ms)
        const remainingMs = (battleDuration * 60 * 1000) - elapsedMs; // (ms)
        if (remainingMs <= 0) {
            // 전투 종료 처리
            return;
        }

        const remainingSeconds = Math.ceil(remainingMs / 1000);
        const minutePart = Math.floor(remainingSeconds / 60);
        const secondPart = remainingSeconds % 60;
        let formattedRemainingTime = `${minutePart.toString().padStart(2, '0')}:${secondPart.toString().padStart(2, '0')}`;
        gameStateManager.setState('remainingTimeString', formattedRemainingTime);
    }, 1000);

    let responses = await requestSync(true);
    let syncResponses = parseMoveResponseList(responses);
    let syncResponse = syncResponses[0];
    console.log('[initGameStatus] syncResponse = ', syncResponse);

    window.stage.processing = {};
    window.stage.processing.response = syncResponse;

    // 게이지는 첨에 튀는거 방지하기 위해 미리
    stage.gGameStatus.hps = syncResponse.hps;
    stage.gGameStatus.hpRates = syncResponse.hpRates;
    stage.gGameStatus.chargeGagues = syncResponse.chargeGagues;
    stage.gGameStatus.fatalChainGauge = syncResponse.fatalChainGauge;

    // 즉시 재 렌더링 필요
    //전조
    window.gameStateManager.setState('omen', syncResponse.omen);
    let omenActivated = !!syncResponse.omen.type;
    window.gameStateManager.setState('currentMotions[0]', omenActivated ? syncResponse.omen.motion : Player.c_animations.WAIT);
    // 가드
    let initialGuardStates = $('#actorContainer .guard-status').toArray().map(element => element.dataset.initialGuardState === 'true');
    initialGuardStates.unshift(null); // 적 null
    setTimeout(() => window.gameStateManager.setState('guardStates', initialGuardStates), 1000); // 살짝 딜레이
    // 리더 id
    let leaderActorId = gameStateManager.getState('leaderActorId');
    let isLeaderAlive = !! gameStateManager.getState('actorIds').find(actorId => actorId === leaderActorId);
    if (!isLeaderAlive) {
        gameStateManager.setState('leaderActorId', null);
    } else {
        // 리더 사망시 아래는 렌더링 하지 않음
        gameStateManager.setState('summonCooldowns', syncResponse.summonCooldowns);
        gameStateManager.setState('unionSummonId', syncResponse.unionSummonId);
    }

    gameStateManager.setState('enemyMaxChargeGauge', syncResponse.enemyMaxChargeGauge);
    gameStateManager.setState('chargeGauges', syncResponse.chargeGauges);
    gameStateManager.setState('fatalChainGauge', syncResponse.fatalChainGauge);
    gameStateManager.setState('currentStatusEffectsList', syncResponse.currentStatusEffectsList);

    gameStateManager.setState('abilitySealeds', syncResponse.abilitySealeds); // 첫 로드는 쿨다운 보다 먼저해야 usableIndicator 에서 제대로 렌더링 가능
    gameStateManager.setState('abilityCoolDowns', syncResponse.abilityCoolDowns);
    gameStateManager.setState('enemyEstimatedAtk', syncResponse.enemyEstimatedAtk);
    gameStateManager.setState('enemyTriggerHps', JSON.parse($('.enemy-info-container').attr('data-trigger-hps')));
    
    // 멤버정보 로드
    requestMembersInfo();

    // 나머지 참전자 무브 잇는경우 처리 (아마 이펙트 겹칠것)
    if (syncResponses.length > 1) {
        processResponseMoves(syncResponses.slice(1));
    }
}