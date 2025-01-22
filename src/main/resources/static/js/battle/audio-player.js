class AudioPlayer {
    constructor() {
        this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
        this.buffers = [];
        this.audioBufferCache = new Map();
    }

    async loadSound(url) {
        console.log('loading sound name / url : ' + '\n' + url);
        if (this.audioBufferCache.has(url)) {
            this.buffers.push(this.audioBufferCache.get(url));
            return;
        }
        const response = await fetch(url);
        const arrayBuffer = await response.arrayBuffer();
        const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer);
        this.buffers.push(audioBuffer);
        this.audioBufferCache.set(url, audioBuffer);
    }

    async loadSounds(urls) {
        console.log('urls = ', urls)
        const loadPromises = urls.map(url => this.loadSound(url));
        await Promise.all(loadPromises);
    }

    playSound(index) {
        const buffer = this.buffers[index];
        if (!buffer) {
            console.error(`No audio loaded for ${index}`);
            return;
        }
        const source = this.audioContext.createBufferSource();
        source.buffer = buffer;
        source.connect(this.audioContext.destination);
        source.start(0);
    }

    /**
     *  여러개를 동시에 재생
     * @param names '[{name : name, url : url}, {name : name, url : url}, ...]'
     */
    playSounds(indexes) {
        indexes.forEach(indexes => this.playSound(indexes));
    }

    playAllSounds() {
        for (const buffer of this.buffers) {
            console.log('buffer = ', buffer)
            console.log('====done')
            const source = this.audioContext.createBufferSource();
            source.buffer = buffer;
            source.connect(this.audioContext.destination);
            source.start(0);
        }
    }
}

// 명시적으로 이름줘서 로드하던걸 이름 없이 url 만으로 로드하도록 위와같이 변경
// class AudioPlayer {
//     constructor() {
//         this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
//         this.buffers = new Map();
//         this.audioBufferCache = new Map();
//     }
//
//     async loadSound(name, url) {
//         console.log('loading sound name / url : ' + name + '\n' + url);
//         if (this.audioBufferCache.has(url)) {
//             this.buffers.set(name, this.audioBufferCache.get(url));
//             return;
//         }
//         const response = await fetch(url);
//         const arrayBuffer = await response.arrayBuffer();
//         const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer);
//         this.buffers.set(name, audioBuffer);
//         this.audioBufferCache.set(url, audioBuffer);
//     }
//
//     async loadSounds(sounds) {
//         const loadPromises = sounds.map(sound => this.loadSound(sound.name, sound.url));
//         await Promise.all(loadPromises);
//     }
//
//     playSound(name) {
//         const buffer = this.buffers.get(name);
//         if (!buffer) {
//             console.error(`No audio loaded for ${name}`);
//             return;
//         }
//         const source = this.audioContext.createBufferSource();
//         source.buffer = buffer;
//         source.connect(this.audioContext.destination);
//         source.start(0);
//     }
//
//     /**
//      *  여러개를 동시에 재생
//      * @param names '[{name : name, url : url}, {name : name, url : url}, ...]'
//      */
//     playSounds(names) {
//         names.forEach(name => this.playSound(name));
//     }
//
//     playAllSounds() {
//         for (const [name, buffer] of this.buffers) {
//             console.log('buffer = ', buffer)
//             console.log('====done')
//             const source = this.audioContext.createBufferSource();
//             source.buffer = buffer;
//             source.connect(this.audioContext.destination);
//             source.start(0);
//         }
//     }
// }

//
// class AudioPlayer {
//     constructor() {
//         this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
//         this.buffer = null;
//     }
//
//     async loadSound(url) {
//         const response = await fetch(url);
//         const arrayBuffer = await response.arrayBuffer();
//         const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer);
//         this.buffer = audioBuffer;
//     }
//
//     playSound() {
//         if (!this.buffer) {
//             // console.error('No audio loaded');
//             return;
//         }
//         const source = this.audioContext.createBufferSource();
//         source.buffer = this.buffer;
//         source.connect(this.audioContext.destination);
//         source.start(0);
//         // console.log('Audio playback started');
//     }
// }