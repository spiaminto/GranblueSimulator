/**
 * 미사용
 */
class AudioPlayer { 
    constructor() {
        this.audioContext = null;
        this.buffers = [];
        this.bufferCache = new Map(); // {src : string, audioBuffer : audioBuffer}
    }

    isReady() {
        return this.audioContext != null;
    }

    init() {
        this.audioContext = new window.AudioContext();
        return this;
    }

    async loadSound(src) {
        if (this.audioContext == null || src == null || src === '') return;
        if (this.bufferCache.has(src)) {
            // console.log('[AudioPlayer.loadSound] cache Hit src = ', src)
            this.buffers.push(this.bufferCache.get(src));
            return;
        }
        // console.log('[AudioPlayer.loadSounds] audioSrc = ', src);
        const response = await fetch(src);
        const arrayBuffer = await response.arrayBuffer();
        const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer);

        this.buffers.push(audioBuffer);
        this.bufferCache.set(src, audioBuffer);
    }

    async loadSounds(audioSrcs) {
        // console.log('[AudioPlayer.loadSounds] audioSrcs = ', audioSrcs);
        if (audioSrcs.length > 0) {
            for (const src of audioSrcs) {
                await this.loadSound(src); // 원래 비동기로 했는데 캐시 생성해서 순차실행으로 변경
            }
        } else {
            console.log('[AudioPlayer.loadSounds] invalid audioSrcs = ', audioSrcs);
        }
    }

    async loadAndPlay(audioSrc) {
        if (audioSrc == null) return;
        this.loadSound(audioSrc).then(() => {
            this.playAllSounds();
        })
    }

    #playSound(audioBuffer, delay = 0) {
        if (this.audioContext == null || !audioBuffer) {
            console.warn(`[AudioPlayer.playSound] audioContext = ${this.audioContext}, audioBuffer = ${audioBuffer}`)
            return;
        }
        // console.log('[AudioPlayer.playSound] audioBuffer = ', audioBuffer, ' delay = ', delay);
        const source = this.audioContext.createBufferSource();
        source.buffer = audioBuffer;
        source.connect(this.audioContext.destination);
        source.start(this.audioContext.currentTime + delay / 1000); // 단위가 s 단위라 ms 로 보내서 1000 으로 나눔
        source.onended = () => {
            source.disconnect();
            // 호출쪽에서 buffer 클리어
            // CHECK 캐시는 남겨두고, 메모리 상황 보고 나중에 정리
        };
    }

    playSound() {
        this.#playSound(this.buffers[0])
        this.buffers = [];
    }

    /**
     *  모든 사운드를 동시에 재생
     */
    playAllSounds() {
        // console.log('[AudioPlayer.playAllSounds] play all sounds', this.buffers);
        this.buffers.forEach((audioBuffer) => this.#playSound(audioBuffer))
        this.buffers = [];
    }

    /**
     *  모든 사운드를 동시에 재생
     */
    playAllSoundsWithoutClear() {
        // console.log('[AudioPlayer.playAllSounds] play all sounds', this.buffers);
        this.buffers.forEach((audioBuffer) => this.#playSound(audioBuffer))
    }

    /**
     * 평타 사운드 재생
     * [보이스][이펙트][이펙트][이펙트][이펙트]...
     * @param attackHitCount 공격 행동의 히트카운트 (데미지 갯수)
     * @param attackMultiHitCount 난격타수 (난격 n회)
     * @param attackDelay 계산된 본 공격 타수당 딜레이 (난격에 의해 크게 좌우되므로 입력받기로)
     */
    playAttackSounds(attackHitCount, attackMultiHitCount, attackDelay) {
        this.buffers.forEach((audioBuffer, index) => {
            if (index === 0) {
                // 보이스
                this.#playSound(audioBuffer);
            } else {
                // 이펙트
                let attackIndex = Math.floor((index - 1) / attackMultiHitCount);
                let multiHitIndex = (index - 1) % attackMultiHitCount;
                let totalDelay = (attackIndex * attackDelay) + (multiHitIndex * 115);
                this.#playSound(audioBuffer, totalDelay);
            }
        })
        this.buffers = [];
    }

    /**
     * 추가 사운드를 재생
     */
    playAdditionalSound(cjsName = null, motion = null, moveType = null) {
        // console.debug('[playAdditionalSound] actorName = ', cjsName, ' moveType = ', moveType, ' motion = ', motion);
        if (!Sounds[cjsName]) return;
        let additionalSound = Sounds[cjsName].additional;
        if (!additionalSound) return;

        let soundByMotion = additionalSound[motion]?.src;
        let soundByMoveType = additionalSound[moveType?.name]?.src;
        let soundByMoveTypeParent = additionalSound[moveType?.getParentType()?.name]?.src;

        console.debug('[playAdditionalSound] soundByMotion = ', soundByMotion, ' soundByMoveType = ', soundByMoveType, ' soundByMoveTypeParent = ', soundByMoveTypeParent);
        [soundByMotion, soundByMoveType, soundByMoveTypeParent].forEach(src => window.audio.play(src, {isLocal: true}));
        // this.loadSounds([soundByMotion, soundByMoveType, soundByMoveTypeParent]).then(() => {
        //     this.playAllSounds();
        // })
    }
}

const enemySoundSrc = '/assets/audio/enemy'
const globalSoundSrc = '/assets/audio/gl/'
const uiSoundSrc = '/assets/audio/ui/'
const bgmSrc= '/assets/audio/bgm/'
const Sounds = { // key = mainCjs.name
    enemy_4300903: { // diaspora1
        additional: { // key: motion
            break_standby_A: {src: enemySoundSrc + '/diaspora2/break-1.mp3'},
            break_standby_B: {src: enemySoundSrc + '/diaspora2/break-1.mp3'},
            standby_A: {src: enemySoundSrc + '/diaspora2/standby-2.mp3'},
            // standby_B: {src: enemySoundSrc + '/diaspora2/standby-2.mp3'},
        },
        bgm: { // key: triggerHp or moveType.name
            100: {src: bgmSrc + 'diaspora-0.mp3', index: 0, formOrder: 1},
            90: {src: bgmSrc + 'diaspora-5.mp3', index: 1, formOrder: 1},
            75: {src: bgmSrc + 'diaspora-6.mp3', index: 2, formOrder: 1},
            'STANDBY_C': {src: bgmSrc + 'diaspora-7.mp3', index: 3, formOrder: 1},
        }

    },
    enemy_4300913: { // diaspora2
        additional: {
            break_standby_A: {src: enemySoundSrc + '/diaspora2/break-1.mp3'},
            break_standby_B: {src: enemySoundSrc + '/diaspora2/break-1.mp3'},
            standby_A: {src: enemySoundSrc + '/diaspora2/standby-1.mp3'},
            // standby_B: {src: enemySoundSrc + '/diaspora2/standby-2.mp3'},
        },
        bgm: {
            100: {src: bgmSrc + 'diaspora-8.mp3', index: 0, formOrder: 2},
            'STANDBY_B': {src: bgmSrc + 'diaspora-9.mp3', index: 1, formOrder: 2},
        }

    },

    global: {
        GUARD_WAIT: {src: globalSoundSrc + 'guard-wait.mp3'},

        DEBUFF: {src: globalSoundSrc + 'debuff.mp3'},

        CHARACTER_DEAD: {src: globalSoundSrc + 'character-dead.mp3'},
    },

    ui: {
        GUARD_ON: {src: uiSoundSrc + 'guard-on.mp3'},
        GUARD_OFF: {src: uiSoundSrc + 'guard-off.mp3'},

        CHARGE_ATTACK_READY: {src: uiSoundSrc + 'charge-attack-ready.mp3',},

        REQUEST_ATTACK: {src: uiSoundSrc + 'request-attack.mp3'},
        CANCEL_ATTACK: {src: uiSoundSrc + 'cancel-attack.mp3'},

        LOADING_READY: {src: uiSoundSrc + 'loading-ready.mp3'},

        BEEP: {src: uiSoundSrc + 'beep.mp3',},

        BUTTON_CLICK: {src: uiSoundSrc + 'button-click.mp3'},
        BUTTON_CLOSE: {src: uiSoundSrc + 'button-close.mp3'},

        TURN_INDICATOR: {src: uiSoundSrc + 'turn-indicator.mp3'},
    },

    bgm: { // key: actorId
        enemy_4300903: {}
    }

}
Object.freeze(Sounds);
