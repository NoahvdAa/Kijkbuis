import {createFileRoute, useNavigate} from '@tanstack/react-router';
import {memo, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from 'react';
import {api} from '#/lib/api';
import {useDismiss} from '#/lib/dismiss';
import {floorToHalfHour, formatDay, formatTime, HOUR, MINUTE, progressOf, useNow} from '#/lib/time';
import {useTv} from '#/lib/tv';
import type {Channel, Guide, Programme} from '#/lib/types';
import {Button} from '#/components/Button';
import {ChannelLogo} from '#/components/ChannelLogo';
import {EpgImage} from '#/components/EpgImage';
import {MenuError, MenuScreen} from '#/components/MenuScreen';

export const Route = createFileRoute('/_app/guide')({
	loader: () => api<Channel[]>('/api/channels'),
	component: GuidePage,
	errorComponent: MenuError
});

const PX_PER_MINUTE = 4;
/** Programmes are loaded per chunk of time, as the guide is scrolled towards it */
const CHUNK = 6 * HOUR;
/** How close (in pixels) scrolling gets to either end before the next chunk is added */
const LOAD_DISTANCE = 1500;
const MAX_FUTURE = 7 * 24 * HOUR;

/**
 * @return where a time is on the timeline, in pixels from its start
 */
function toX(time: number, start: number): number {
	return (time - start) / MINUTE * PX_PER_MINUTE;
}

type Range = {start: number, end: number};
type Selection = {channel: Channel, programme: Programme};

/**
 * The TV guide: a row per channel, with time running from left to right. Scrolling right goes forward in time,
 * scrolling left goes back, and more of the guide is loaded as you get near either end.
 */
function GuidePage() {
	const channels = Route.useLoaderData();
	const {watch} = useTv();
	const navigate = useNavigate();
	const now = useNow(60_000);
	const scroller = useRef<HTMLDivElement>(null);

	// Starts a little before now, with a chunk of the past loaded to scroll back into. The backend only keeps about
	// 12 hours of past programmes, so it goes back one more chunk at most.
	const [limits] = useState(() => {
		const start = floorToHalfHour(Date.now() - 2 * HOUR) - CHUNK;
		return {initial: {start, end: start + 4 * CHUNK}, earliest: start - CHUNK, latest: Date.now() + MAX_FUTURE};
	});
	const [range, setRange] = useState<Range>(limits.initial);
	const programmes = useGuideData(range);
	const [leftEdge, setLeftEdge] = useState(Date.now);
	const [selected, setSelected] = useState<Selection | null>(null);

	// Adding earlier time widens the guide on the left, which would push what's on screen to the right
	const pendingShift = useRef(0);
	const growing = useRef(false);

	useLayoutEffect(() => {
		if (scroller.current) {
			scroller.current.scrollLeft = toX(Date.now() - 30 * MINUTE, limits.initial.start);
		}
	}, [limits]);

	useLayoutEffect(() => {
		if (pendingShift.current && scroller.current) {
			scroller.current.scrollLeft += pendingShift.current;
			pendingShift.current = 0;
		}
		growing.current = false;
	}, [range]);

	function onScroll() {
		const element = scroller.current;
		if (!element) {
			return;
		}
		setLeftEdge(range.start + element.scrollLeft / PX_PER_MINUTE * MINUTE);

		if (growing.current) {
			return;
		}
		if (element.scrollLeft + element.clientWidth > element.scrollWidth - LOAD_DISTANCE && range.end < limits.latest) {
			growing.current = true;
			setRange({start: range.start, end: range.end + CHUNK});
		} else if (element.scrollLeft < LOAD_DISTANCE && range.start > limits.earliest) {
			growing.current = true;
			pendingShift.current = toX(range.start, range.start - CHUNK);
			setRange({start: range.start - CHUNK, end: range.end});
		}
	}

	function scrollToTime(time: number) {
		scroller.current?.scrollTo({left: toX(time, range.start), behavior: 'smooth'});
	}

	function scrollByMinutes(minutes: number) {
		scroller.current?.scrollBy({left: minutes * PX_PER_MINUTE, behavior: 'smooth'});
	}

	const select = useCallback((channel: Channel, programme: Programme) => setSelected({channel, programme}), []);
	const closeDetails = useCallback(() => setSelected(null), []);
	const watchChannel = useCallback((channel: Channel) => {
		watch(channel.id);
		navigate({to: '/'});
	}, [watch, navigate]);

	return <MenuScreen title="Guide" fill>
		<div className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-3 sm:px-6">
			<Button onClick={() => scrollByMinutes(-120)}>Earlier</Button>
			<Button onClick={() => scrollToTime(Date.now() - 30 * MINUTE)}>Now</Button>
			<Button onClick={() => scrollByMinutes(120)}>Later</Button>
			<p className="ml-auto font-mono text-sm uppercase tracking-[0.2em] text-muted">{formatDay(leftEdge)}</p>
		</div>

		{channels.length > 0
			? <div ref={scroller} onScroll={onScroll} className="guide relative min-h-0 flex-1 overflow-auto overscroll-contain">
				<div className="relative" style={{width: `calc(var(--guide-channel-width) + ${toX(range.end, range.start)}px)`}}>
					<TimeRuler start={range.start} end={range.end}/>
					<GuideRows
						channels={channels}
						programmes={programmes}
						start={range.start}
						end={range.end}
						now={now}
						onSelect={select}
						onWatch={watchChannel}
					/>
				</div>
			</div>
			: <p className="p-8 text-muted">There are no channels yet. An admin needs to add a playlist first.</p>}

		{selected && <ProgrammeDetails selection={selected} now={now} onWatch={watchChannel} onClose={closeDetails}/>}
	</MenuScreen>;
}

/**
 * Loads the guide for every chunk of time in the range, once.
 *
 * @return each channel's programmes, in order
 */
function useGuideData(range: Range): Map<string, Programme[]> {
	const [chunks, setChunks] = useState<Record<number, Guide['channels']>>({});
	const requested = useRef(new Set<number>());

	useEffect(() => {
		for (let chunk = range.start; chunk < range.end; chunk += CHUNK) {
			if (requested.current.has(chunk)) {
				continue;
			}
			requested.current.add(chunk);
			const query = new URLSearchParams({from: new Date(chunk).toISOString(), to: new Date(chunk + CHUNK).toISOString()});
			api<Guide>(`/api/guide?${query}`)
				.then((guide) => setChunks((current) => ({...current, [chunk]: guide.channels})))
				// Tried again the next time the guide grows
				.catch(() => requested.current.delete(chunk));
		}
	}, [range.start, range.end]);

	return useMemo(() => {
		const merged = new Map<string, Map<string, Programme>>();
		for (const channels of Object.values(chunks)) {
			for (const [channelId, channelProgrammes] of Object.entries(channels)) {
				const byStart = merged.get(channelId) ?? new Map<string, Programme>();
				merged.set(channelId, byStart);
				// Programmes that span two chunks are in both
				channelProgrammes.forEach((programme) => byStart.set(programme.startsAt, programme));
			}
		}
		return new Map([...merged].map(([channelId, byStart]) => [
			channelId,
			[...byStart.values()].sort((a, b) => a.startsAt.localeCompare(b.startsAt)),
		]));
	}, [chunks]);
}

const TimeRuler = memo(function TimeRuler({start, end}: {start: number, end: number}) {
	const marks: number[] = [];
	for (let time = start; time < end; time += 30 * MINUTE) {
		marks.push(time);
	}

	return <div className="sticky top-0 z-20 flex h-10 border-b border-line bg-panel">
		<div className="sticky left-0 z-30 w-(--guide-channel-width) shrink-0 border-r border-line bg-panel"/>
		<div className="relative shrink-0" style={{width: toX(end, start)}}>
			{marks.map((time) => {
				const date = new Date(time);
				const midnight = date.getHours() === 0 && date.getMinutes() === 0;
				return <span
					key={time}
					className={`absolute inset-y-0 border-l border-line pl-2 font-mono text-xs leading-10 tabular-nums ${date.getMinutes() === 0 ? 'text-fg' : 'text-muted'}`}
					style={{left: toX(time, start)}}
				>
					{midnight ? formatDay(time) : formatTime(time)}
				</span>;
			})}
		</div>
	</div>;
});

type GuideRowsProps = {
	channels: Channel[]
	programmes: Map<string, Programme[]>
	start: number
	end: number
	now: number
	onSelect: (channel: Channel, programme: Programme) => void
	onWatch: (channel: Channel) => void
};

// Memoized, so scrolling (which updates the day shown above the guide) doesn't redraw every row
const GuideRows = memo(function GuideRows({channels, programmes, start, end, now, onSelect, onWatch}: GuideRowsProps) {
	return channels.map((channel) => <div key={channel.id} className="flex h-16 border-b border-line [contain-intrinsic-size:auto_4rem] [content-visibility:auto]">
		<button
			type="button"
			onClick={() => onWatch(channel)}
			aria-label={`Watch ${channel.name}`}
			title={`Watch ${channel.name}`}
			className="sticky left-0 z-10 flex w-(--guide-channel-width) shrink-0 items-center gap-3 border-r border-line bg-panel px-3 text-left hover:bg-raised focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
		>
			<span className="hidden w-[3ch] shrink-0 font-mono text-xs text-muted tabular-nums sm:block">{channel.channelNumber}</span>
			<ChannelLogo channel={channel} className="size-11 shrink-0"/>
			<span className="hidden min-w-0 truncate text-sm sm:block">{channel.name}</span>
		</button>
		<div className="relative shrink-0" style={{width: toX(end, start)}}>
			{(programmes.get(channel.id) ?? []).map((programme) => <ProgrammeCell
				key={programme.startsAt}
				channel={channel}
				programme={programme}
				start={start}
				end={end}
				now={now}
				onSelect={onSelect}
			/>)}
			{/*
				The current time. It's drawn in every row rather than once across the guide: each row is painted on its own
				(for content-visibility), so only something inside the row can go under its sticky channel column.
			*/}
			{start <= now && now < end && <div
				aria-hidden="true"
				className="pointer-events-none absolute top-0 -bottom-px w-0.5 bg-teletext-red"
				style={{left: toX(now, start)}}
			/>}
		</div>
	</div>);
});

type ProgrammeCellProps = {
	channel: Channel
	programme: Programme
	start: number
	end: number
	now: number
	onSelect: (channel: Channel, programme: Programme) => void
};

function ProgrammeCell({channel, programme, start, end, now, onSelect}: ProgrammeCellProps) {
	const begins = Date.parse(programme.startsAt);
	const ends = Date.parse(programme.endsAt);
	const left = toX(Math.max(begins, start), start);
	const right = toX(Math.min(ends, end), start);
	if (right <= left) {
		return null;
	}
	const airing = begins <= now && now < ends;

	return <button
		type="button"
		data-programme
		onClick={() => onSelect(channel, programme)}
		style={{left, width: right - left}}
		// Clipped rather than hidden, which would stop the text inside from sticking
		className={`absolute inset-y-0 overflow-clip border-r border-line text-left hover:bg-raised focus-visible:z-[1] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent ${airing ? 'bg-raised' : ''} ${ends <= now ? 'text-muted' : ''}`}
	>
		{/* Sticks to the left edge, so the title of a programme that started earlier stays readable */}
		<span className="sticky left-(--guide-channel-width) inline-block max-w-full px-3 py-2.5 align-top">
			<span className="block truncate text-sm">{programme.title}</span>
			<span className="block truncate font-mono text-xs text-muted tabular-nums">{formatTime(begins)}–{formatTime(ends)}</span>
		</span>
		{airing && <span aria-hidden="true" className="absolute bottom-0 left-0 h-0.5 bg-accent" style={{width: `${progressOf(programme, now) * 100}%`}}/>}
	</button>;
}

type ProgrammeDetailsProps = {
	selection: Selection
	now: number
	onWatch: (channel: Channel) => void
	onClose: () => void
};

function ProgrammeDetails({selection: {channel, programme}, now, onWatch, onClose}: ProgrammeDetailsProps) {
	const begins = Date.parse(programme.startsAt);
	const ends = Date.parse(programme.endsAt);
	// Clicking elsewhere closes it, and Escape does too before it takes you back to the TV. Clicking another programme
	// shows that one in its place instead.
	const panel = useRef<HTMLElement>(null);
	useDismiss(true, panel, onClose, '[data-programme]');

	return <section ref={panel} aria-label={programme.title} className="fixed inset-x-0 bottom-0 z-30 overflow-hidden border-t border-line bg-panel shadow-2xl">
		{/* Dimmed far enough that the text stays readable on top of it */}
		{programme.backdropUrl && <EpgImage src={programme.backdropUrl} className="absolute inset-0 size-full object-cover opacity-20"/>}

		<div className="relative mx-auto flex max-w-3xl items-start gap-4 p-4 sm:gap-6 sm:p-6">
			{programme.posterUrl
				? <EpgImage src={programme.posterUrl} className="aspect-[2/3] w-20 shrink-0 bg-raised object-cover sm:w-32"/>
				// Icons can be any shape, so they keep theirs
				: programme.iconUrl && <EpgImage src={programme.iconUrl} className="max-h-24 w-auto max-w-28 shrink-0 object-contain sm:max-h-40 sm:max-w-56"/>}

			<div className="grid min-w-0 flex-1 gap-3">
				<p className="font-mono text-xs uppercase tracking-[0.2em] text-muted">
					{channel.name} · {formatDay(begins, now)} {formatTime(begins)}–{formatTime(ends)}
				</p>
				<h2 className="text-xl font-medium">{programme.title}</h2>
				{programme.subtitle && <p>{programme.subtitle}</p>}
				{programme.description && <p className="max-h-32 overflow-y-auto text-sm text-fg/75">{programme.description}</p>}
				<div className="mt-2 flex flex-wrap gap-2">
					<Button autoFocus onClick={() => onWatch(channel)}>
						{begins <= now && now < ends ? 'Watch now' : `Watch ${channel.name}`}
					</Button>
					<Button onClick={onClose}>Close</Button>
				</div>
			</div>
		</div>
	</section>;
}
