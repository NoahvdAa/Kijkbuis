import Hls from 'hls.js';
import {type ReactNode, type RefObject, useEffect, useRef, useState} from 'react';
import {codecName} from '#/lib/media';
import type {Channel} from '#/lib/types';
import type {StreamInfo} from './Player';

/**
 * What a player keeps track of for the stats panel, besides what hls.js and the video element know themselves.
 */
export type PlayerDebug = {
    hls: Hls | null
    /** How often playback got stuck for a moment */
    stalls: number
    /** How often the stream was started again after it dropped */
    restarts: number
    /** How many errors hls.js reported, including ones it recovered from */
    errors: number
    lastError: string | null
    /** The codecs hls.js found in the stream */
    videoCodec: string | null
    audioCodec: string | null
    lastSegment: { number: number, bytes: number, seconds: number, duration: number } | null
};

export function newPlayerDebug(): PlayerDebug {
    return {
        hls: null,
        stalls: 0,
        restarts: 0,
        errors: 0,
        lastError: null,
        videoCodec: null,
        audioCodec: null,
        lastSegment: null
    };
}

/**
 * Forgets what was counted for the previous channel, keeping the player.
 */
export function resetPlayerDebug(debug: PlayerDebug): void {
    Object.assign(debug, {...newPlayerDebug(), hls: debug.hls});
}

type PlayerStatsProps = {
    video: RefObject<HTMLVideoElement | null>
    container: RefObject<HTMLElement | null>
    debug: RefObject<PlayerDebug>
    channel: Channel | null
    /** What the backend said about the stream, if it's playing */
    stream: StreamInfo | null
    /** What the stream is doing, such as tuning or failed */
    status: string
    onClose: () => void
};

/**
 * Everything there is to know about a screen's playback, for finding out why it stutters or looks off. Like YouTube's
 * stats for nerds, it's hidden until asked for.
 */
export function PlayerStats({video, container, debug, channel, stream, status, onClose}: PlayerStatsProps) {
    // Refreshes every second, and measures the frame rate over that second
    const [, setTick] = useState(0);
    const [fps, setFps] = useState<number | null>(null);
    const lastFrames = useRef<{ frames: number, at: number } | null>(null);
    useEffect(() => {
        const interval = window.setInterval(() => {
            const element = video.current;
            if (element) {
                const frames = element.getVideoPlaybackQuality().totalVideoFrames;
                const at = performance.now();
                const previous = lastFrames.current;
                setFps(previous && frames >= previous.frames ? (frames - previous.frames) / ((at - previous.at) / 1000) : null);
                lastFrames.current = {frames, at};
            }
            setTick((tick) => tick + 1);
        }, 1000);
        return () => window.clearInterval(interval);
    }, [video]);

    const element = video.current;
    const {hls, stalls, restarts, errors, lastError, videoCodec, audioCodec, lastSegment} = debug.current;
    const quality = element?.getVideoPlaybackQuality();
    const box = container.current?.getBoundingClientRect();
    const mediaSource = Hls.getMediaSource();

    // Halfway down the left edge, clear of the TV's top bar and the control bar on large screens
    return <div
        className="absolute top-1/2 left-[max(0.5rem,2cqw)] z-20 max-h-[calc(100%-1rem)] max-w-[calc(100%-1rem)] -translate-y-1/2 overflow-auto bg-screen/85 font-mono text-[clamp(0.6rem,1.4cqw,0.75rem)] leading-relaxed">
        <div className="flex items-center justify-between gap-4 border-b border-line px-3 py-1.5">
            <span className="uppercase tracking-[0.2em] text-muted">Stats for nerds</span>
            <button
                type="button"
                onClick={onClose}
                aria-label="Close stats"
                title="Close stats (d)"
                className="px-1 text-muted hover:text-fg focus-visible:outline-2 focus-visible:outline-accent"
            >
                ×
            </button>
        </div>

        <table
            className="m-3 mt-2 border-separate border-spacing-x-4 [&_th]:pr-0 [&_th]:text-left [&_th]:font-normal [&_th]:whitespace-nowrap [&_th]:text-muted">
            <tbody>
            <Row label="Channel">{channel ? channel.name : '…'}</Row>
            <Row label="Status">{status}</Row>
            {stream && <>
                <Row label="Stream">{stream.id.slice(0, 8)} · quality {stream.quality}</Row>
                <Row label="Server">
                    {stream.transcoding
                        ? `Transcoding to H.264${stream.sourceInterlaced ? ', deinterlacing' : ''}`
                        : `Passing video through${stream.sourceInterlaced ? ' (interlaced)' : ''}`}
                </Row>
                <Row label="Source">
                    {[
                        stream.sourceVideoCodec && codecName(stream.sourceVideoCodec),
                        stream.sourceWidth && stream.sourceHeight && `${stream.sourceWidth}×${stream.sourceHeight}`,
                        stream.sourceInterlaced && 'interlaced',
                    ].filter(Boolean).join(' · ') || 'unknown'}
                </Row>
            </>}
            {element && <>
                <Row label="Picture">
                    {element.videoWidth > 0 ? `${element.videoWidth}×${element.videoHeight}` : 'none yet'}
                    {(videoCodec || audioCodec) && ` · ${[videoCodec, audioCodec].filter(Boolean).join(', ')}`}
                </Row>
                <Row label="Frames">
                    {fps !== null ? `${fps.toFixed(1)} fps` : '…'}
                    {quality && ` · ${quality.droppedVideoFrames} of ${quality.totalVideoFrames} dropped`}
                </Row>
                <Row label="Buffer">{formatSeconds(bufferAhead(element))} ahead</Row>
            </>}
            {hls && <>
                <Row label="Latency">
                    {Number.isFinite(hls.latency) ? formatSeconds(hls.latency) : '…'}
                    {hls.targetLatency !== null && ` · target ${formatSeconds(hls.targetLatency)}`}
                </Row>
                <Row label="Segment">
                    {lastSegment
                        ? `#${lastSegment.number} · ${formatSeconds(lastSegment.duration)} · ${formatBytes(lastSegment.bytes)} in ${Math.round(lastSegment.seconds * 1000)} ms`
                        : '…'}
                </Row>
                <Row label="Bandwidth">
                    {lastSegment && lastSegment.seconds > 0 && `${formatBits(lastSegment.bytes * 8 / lastSegment.seconds)} last segment · `}
                    {formatBits(hls.bandwidthEstimate)} estimated
                </Row>
            </>}
            <Row label="Trouble">{stalls} stalls · {restarts} restarts · {errors} errors</Row>
            {lastError && <Row label="Last error">{lastError}</Row>}
            {box && <Row
                label="Viewport">{Math.round(box.width)}×{Math.round(box.height)} × {window.devicePixelRatio}</Row>}
            <Row label="Player">
                {hls ? `hls.js ${Hls.version}` : 'Browser HLS'}
                {hls && mediaSource && ` · ${mediaSource.name}`}
            </Row>
            </tbody>
        </table>
    </div>;
}

function Row({label, children}: { label: string, children: ReactNode }) {
    return <tr>
        <th scope="row">{label}</th>
        <td>{children}</td>
    </tr>;
}

/**
 * @return how much video is buffered past what's playing now, in seconds
 */
function bufferAhead(video: HTMLVideoElement): number {
    for (let i = 0; i < video.buffered.length; i++) {
        if (video.buffered.start(i) <= video.currentTime && video.currentTime <= video.buffered.end(i)) {
            return video.buffered.end(i) - video.currentTime;
        }
    }
    return 0;
}

function formatSeconds(seconds: number): string {
    return `${seconds.toFixed(1)} s`;
}

function formatBytes(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatBits(bitsPerSecond: number): string {
    return bitsPerSecond < 1_000_000 ? `${Math.round(bitsPerSecond / 1000)} kbit/s` : `${(bitsPerSecond / 1_000_000).toFixed(1)} Mbit/s`;
}
