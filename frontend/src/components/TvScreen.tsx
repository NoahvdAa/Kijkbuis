import {Link} from '@tanstack/react-router';
import {type KeyboardEvent, type PointerEvent, type RefObject, useCallback, useEffect, useId, useRef, useState} from 'react';
import {type Canvas, type Corner, moveFrame, nudgeFrame, resizeFrame} from '#/lib/arrange';
import {hasPrivilege, type User} from '#/lib/auth';
import {canFullscreenPage, toggleFullscreen, useFullscreenElement} from '#/lib/media';
import {useProfiles} from '#/lib/profiles';
import {type Tile, useTv} from '#/lib/tv';
import {type Frame, gridFrames, gridRects, MAX_SCREENS, type Rect, useTvAspect} from '#/lib/tvLayout';
import type {Profile} from '#/lib/types';
import {Brand} from './Brand';
import {ExitFullscreenIcon, FullscreenIcon} from './Icons';
import {MultiviewPicker} from './MultiviewPicker';
import {Player, useChannel} from './Player';

type TvScreenProps = {
	/** Full screen while watching, or small in the top left while a menu is open */
	mode: 'full' | 'pip'
	user: User
};

/** How long the controls stay visible after the mouse last moved */
const CONTROLS_TIMEOUT = 3_000;
/** The menu that's open when picking the multiview grid. The screens' quality menus are named after their tile. */
const MULTIVIEW_MENU = 'multiview';

/**
 * The TV. It's always on screen, and it's the same element whether it's full screen or in the corner, so moving
 * between the two never interrupts what's playing. With multiview it shows a grid of screens, arranged to suit the
 * shape of the device, or the screens wherever the user arranged them. The corner version shows the same.
 */
export function TvScreen({mode, user}: TvScreenProps) {
	const {tiles, layout, aspect, activeTileId, setActiveTile, setFrames, saveLayout} = useTv();
	useTvAspect(aspect);
	const section = useRef<HTMLElement>(null);
	const canvas = useRef<HTMLDivElement>(null);
	const [awake, wake] = useAwake(section);
	// A new channel brings up the controls, with its info banner
	const channels = tiles.map((tile) => tile.channelId).join();
	useEffect(() => {
		wake();
	}, [channels, wake]);
	const profiles = useProfiles(hasPrivilege(user, 'transcode'));
	const pip = mode === 'pip';
	const multiview = tiles.length > 1;

	// One menu is open at a time, and the controls stay visible while it is
	const [openMenu, setOpenMenu] = useState<string | null>(null);
	const setMenuOpen = useCallback((menu: string, open: boolean) => {
		setOpenMenu((current) => open ? menu : current === menu ? null : current);
	}, []);
	const setMultiviewOpen = useCallback((open: boolean) => setMenuOpen(MULTIVIEW_MENU, open), [setMenuOpen]);
	useEffect(() => {
		// The corner version has no controls, so menus shouldn't pop up again when coming back
		if (pip) {
			setOpenMenu(null);
		}
	}, [pip]);

	// Moving and resizing the screens, which turns the layout into a custom one
	const [arranging, setArranging] = useState(false);
	const [dragging, setDragging] = useState(false);
	const startArranging = useCallback(() => {
		if (layout.kind === 'grid') {
			setFrames(gridFrames(layout.grid));
		}
		setOpenMenu(null);
		setArranging(true);
	}, [layout, setFrames]);
	const stopArranging = useCallback(() => {
		saveLayout();
		setArranging(false);
	}, [saveLayout]);
	useEffect(() => {
		// Opening a menu puts the TV in the corner, where it can't be arranged
		if (pip && arranging) {
			stopArranging();
		}
	}, [pip, arranging, stopArranging]);
	useEffect(() => {
		if (!arranging) {
			return;
		}
		const onKeyDown = (event: globalThis.KeyboardEvent) => {
			if (event.key === 'Escape' && !event.defaultPrevented) {
				stopArranging();
			}
		};
		window.addEventListener('keydown', onKeyDown);
		return () => window.removeEventListener('keydown', onKeyDown);
	}, [arranging, stopArranging]);

	// While nothing is playing, there's nothing to hide the controls for. While arranging, the screens hide theirs.
	const showControls = !pip && !arranging && (awake || openMenu !== null || !tiles.some((tile) => tile.channelId));
	const rects: Rect[] = layout.kind === 'grid'
		? gridRects(layout.grid)
		: tiles.map((tile) => frameRect(layout.frames[tile.id]));
	const activeIndex = tiles.findIndex((tile) => tile.id === activeTileId);

	return <section
		ref={section}
		aria-label="TV"
		onPointerMove={wake}
		onPointerDown={wake}
		onKeyDown={wake}
		className={`tv fixed z-20 overflow-hidden bg-screen transition-[top,left,width,height] duration-300 ease-out motion-reduce:transition-none ${pip ? 'tv-pip shadow-2xl ring-1 ring-line' : 'tv-full'} ${!pip && !showControls && !arranging ? 'cursor-none' : ''}`}
	>
		{/*
			While arranging, the screens make room for the bar above them and the layers next to them (below them on phones),
			so those don't cover their corners
		*/}
		<div className={`absolute left-0 [container-type:size] transition-[top,right,bottom] duration-300 ease-out motion-reduce:transition-none ${arranging ? 'top-14 right-0 bottom-72 md:right-64 md:bottom-0' : 'top-0 right-0 bottom-0'}`}>
			<div ref={canvas} className={`tv-grid absolute inset-0 m-auto ${multiview && layout.kind === 'grid' ? 'bg-line' : ''}`}>
				{tiles.map((tile, index) => <TileView
					key={tile.id}
					tile={tile}
					number={index + 1}
					rect={rects[index]}
					layer={layout.kind === 'custom' ? layout.stack.indexOf(tile.id) + 1 : undefined}
					bordered={multiview && layout.kind === 'custom'}
					multiview={multiview}
					pip={pip}
					active={tile.id === activeTileId}
					awake={showControls}
					arranging={arranging}
					dragging={dragging}
					canvas={canvas}
					profiles={profiles}
					menuOpen={openMenu === tile.id}
					onMenuOpenChange={setMenuOpen}
					onSelect={setActiveTile}
					onWake={wake}
					onDraggingChange={setDragging}
				/>)}
				{arranging && activeIndex >= 0 && <SelectionFrame
					tile={tiles[activeIndex]}
					rect={rects[activeIndex]}
					dragging={dragging}
					canvas={canvas}
					onDraggingChange={setDragging}
				/>}
			</div>
		</div>

		{pip
			? <Link to="/" aria-label="Back to the TV" className="absolute inset-0 z-20 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"/>
			: arranging
				? <>
					<ArrangeBar onDone={stopArranging}/>
					<LayerList/>
				</>
				: <TvOverlay
					user={user}
					visible={showControls}
					pickingMultiview={openMenu === MULTIVIEW_MENU}
					onPickingMultiviewChange={setMultiviewOpen}
					onArrange={startArranging}
				/>}
	</section>;
}

/**
 * @return where a screen of a custom layout is on the TV
 */
function frameRect(frame: Frame): Rect {
	return {x: frame.x, y: frame.y, width: frame.size, height: frame.size};
}

type TileViewProps = {
	tile: Tile
	number: number
	/** Where the screen is on the TV */
	rect: Rect
	/** Where the screen is in the stack of a custom layout, where higher ones are drawn on top */
	layer: number | undefined
	/** Whether the screen has a line around it, to tell it apart from the screens it overlaps */
	bordered: boolean
	multiview: boolean
	pip: boolean
	/** Whether this is the screen picking a channel puts it on */
	active: boolean
	/** Whether the controls are showing */
	awake: boolean
	/** Whether the screens are being moved and resized */
	arranging: boolean
	/** Whether a screen is being dragged right now, which it follows without delay */
	dragging: boolean
	/** The box the screens are in, which dragging them is measured against */
	canvas: RefObject<HTMLDivElement | null>
	profiles: Profile[]
	/** Whether the screen's quality menu is open */
	menuOpen: boolean
	onMenuOpenChange: (tileId: string, open: boolean) => void
	onSelect: (tileId: string) => void
	onWake: () => void
	onDraggingChange: (dragging: boolean) => void
};

function TileView({tile, number, rect, layer, bordered, multiview, pip, active, awake, arranging, dragging, canvas, profiles, menuOpen, onMenuOpenChange, onSelect, onWake,
	onDraggingChange}: TileViewProps) {
	const [hovered, setHovered] = useState(false);
	const select = useCallback(() => onSelect(tile.id), [onSelect, tile.id]);
	const setMenuOpen = useCallback((open: boolean) => onMenuOpenChange(tile.id, open), [onMenuOpenChange, tile.id]);

	return <div
		// Slides to where it goes when the layout changes
		className={`absolute ${multiview ? 'p-px' : ''} ${bordered ? 'bg-line' : ''} ${dragging ? '' : 'transition-[left,top,width,height] duration-300 ease-out motion-reduce:transition-none'}`}
		style={{left: `${rect.x * 100}%`, top: `${rect.y * 100}%`, width: `${rect.width * 100}%`, height: `${rect.height * 100}%`, zIndex: layer}}
		onPointerEnter={(event) => event.pointerType === 'mouse' && setHovered(true)}
		onPointerLeave={() => setHovered(false)}
	>
		<div className="relative h-full">
			<Player
				tile={tile}
				number={number}
				multiview={multiview}
				pip={pip}
				// In multiview, only the screen under the mouse and the selected one show their controls
				showControls={awake && (!multiview || hovered || active || menuOpen)}
				tvAwake={awake}
				active={active}
				profiles={profiles}
				menuOpen={menuOpen}
				onMenuOpenChange={setMenuOpen}
				onSelect={select}
				onWake={onWake}
			/>
			{/*
				Marks the screen that picking a channel puts it on. It's drawn on top, so the picture can't cover it. The
				corner TV always shows it, since that's where the menus pick channels for it. The full TV only shows it with
				the controls, so it doesn't sit around one screen while just watching.
			*/}
			{multiview && active && !arranging && <span
				aria-hidden="true"
				className={`pointer-events-none absolute inset-0 z-[15] ring-2 ring-inset ring-accent transition-opacity duration-200 ${pip || awake ? 'opacity-100' : 'opacity-0'}`}
			/>}
			{arranging && <ArrangeFrame tile={tile} number={number} active={active} canvas={canvas} onDraggingChange={onDraggingChange}/>}
		</div>
	</div>;
}

const CORNERS: {corner: Corner, className: string}[] = [
	{corner: 'top-left', className: 'top-0 left-0 cursor-nwse-resize'},
	{corner: 'top-right', className: 'top-0 right-0 cursor-nesw-resize'},
	{corner: 'bottom-left', className: 'bottom-0 left-0 cursor-nesw-resize'},
	{corner: 'bottom-right', className: 'bottom-0 right-0 cursor-nwse-resize'},
];

type Drag = {
	pointerId: number
	/** The corner being dragged, or null when moving the whole screen */
	corner: Corner | null
	startX: number
	startY: number
	frame: Frame
	/** Where the other screens are, to snap to */
	others: Frame[]
	canvas: Canvas
};

/**
 * Moves a screen of a custom layout by dragging it, or resizes it by dragging one of its corners. Dragging selects the
 * screen, but leaves it in its layer.
 *
 * @return what to call when the pointer goes down on the screen (without a corner) or a corner, moves, and lets go
 */
function useFrameDrag(tile: Tile, canvas: RefObject<HTMLDivElement | null>, onDraggingChange: (dragging: boolean) => void) {
	const {tiles, layout, setFrame, setActiveTile} = useTv();
	const drag = useRef<Drag | null>(null);

	function start(event: PointerEvent<HTMLElement>, corner: Corner | null) {
		const box = canvas.current?.getBoundingClientRect();
		if (event.button !== 0 || !box || layout.kind !== 'custom') {
			return;
		}
		event.preventDefault();
		event.stopPropagation();
		event.currentTarget.setPointerCapture(event.pointerId);
		drag.current = {
			pointerId: event.pointerId,
			corner,
			startX: event.clientX,
			startY: event.clientY,
			frame: layout.frames[tile.id],
			others: tiles.filter((other) => other.id !== tile.id).map((other) => layout.frames[other.id]),
			canvas: {width: box.width, height: box.height},
		};
		setActiveTile(tile.id);
		onDraggingChange(true);
	}

	function move(event: PointerEvent<HTMLElement>) {
		const current = drag.current;
		if (!current || current.pointerId !== event.pointerId) {
			return;
		}
		const dx = (event.clientX - current.startX) / current.canvas.width;
		const dy = (event.clientY - current.startY) / current.canvas.height;
		setFrame(tile.id, current.corner
			? resizeFrame(current.frame, current.corner, dx, dy, current.others, current.canvas)
			: moveFrame(current.frame, dx, dy, current.others, current.canvas));
	}

	function end(event: PointerEvent<HTMLElement>) {
		if (drag.current?.pointerId === event.pointerId) {
			drag.current = null;
			onDraggingChange(false);
		}
	}

	return {start, move, end};
}

type ArrangeFrameProps = {
	tile: Tile
	number: number
	active: boolean
	canvas: RefObject<HTMLDivElement | null>
	onDraggingChange: (dragging: boolean) => void
};

/**
 * Covers a screen while arranging, in its own layer: dragging it moves the screen. With the keyboard, the arrow keys
 * move it, and with Shift they resize it.
 */
function ArrangeFrame({tile, number, active, canvas, onDraggingChange}: ArrangeFrameProps) {
	const {tiles, layout, setFrame, setActiveTile, removeScreen} = useTv();
	const {start, move, end} = useFrameDrag(tile, canvas, onDraggingChange);
	const hintId = useId();
	if (layout.kind !== 'custom') {
		return null;
	}
	const frame = layout.frames[tile.id];

	function onKeyDown(event: KeyboardEvent<HTMLElement>) {
		if (event.target !== event.currentTarget) {
			return;
		}
		if ((event.key === 'Delete' || event.key === 'Backspace') && tiles.length > 1) {
			event.preventDefault();
			removeScreen(tile.id);
			return;
		}
		const nudged = nudgeFrame(frame, event.key, event.shiftKey);
		if (nudged) {
			event.preventDefault();
			setFrame(tile.id, nudged);
		}
	}

	return <div
		tabIndex={0}
		role="group"
		aria-label={`Screen ${number}`}
		aria-describedby={hintId}
		onPointerDown={(event) => start(event, null)}
		onPointerMove={move}
		onPointerUp={end}
		onPointerCancel={end}
		onFocus={() => setActiveTile(tile.id)}
		onKeyDown={onKeyDown}
		// The selected screen's outline is drawn above all screens instead, see SelectionFrame
		className={`absolute inset-0 z-[16] cursor-move touch-none bg-screen/40 [container-type:size] focus-visible:outline-none ${active ? '' : 'ring-2 ring-inset ring-fg/60'}`}
	>
		<span id={hintId} className="sr-only">Arrow keys move it, Shift and arrow keys resize it.</span>
		<span className={`absolute inset-0 grid place-items-center font-mono text-[clamp(1rem,20cqh,4rem)] leading-none ${active ? 'text-accent' : 'text-fg'}`}>
			{number}
		</span>
	</div>;
}

type SelectionFrameProps = {
	tile: Tile
	rect: Rect
	dragging: boolean
	canvas: RefObject<HTMLDivElement | null>
	onDraggingChange: (dragging: boolean) => void
};

/**
 * Outlines the selected screen while arranging, with the handles to resize it. It's drawn above all screens, so a
 * screen behind others can still be found and resized.
 */
function SelectionFrame({tile, rect, dragging, canvas, onDraggingChange}: SelectionFrameProps) {
	const {start, move, end} = useFrameDrag(tile, canvas, onDraggingChange);

	return <div
		aria-hidden="true"
		onPointerMove={move}
		onPointerUp={end}
		onPointerCancel={end}
		// Only the corners catch the pointer, the rest goes to the screens below
		className={`pointer-events-none absolute ring-2 ring-inset ring-accent ${dragging ? '' : 'transition-[left,top,width,height] duration-300 ease-out motion-reduce:transition-none'}`}
		style={{left: `${rect.x * 100}%`, top: `${rect.y * 100}%`, width: `${rect.width * 100}%`, height: `${rect.height * 100}%`, zIndex: MAX_SCREENS + 1}}
	>
		{CORNERS.map(({corner, className}) => <span
			key={corner}
			onPointerDown={(event) => start(event, corner)}
			className={`pointer-events-auto absolute size-4 touch-none border-2 border-screen bg-accent pointer-coarse:size-6 ${className}`}
		/>)}
	</div>;
}

/** How far a layer has to be dragged before it starts moving, so a click still selects it */
const LAYER_DRAG_THRESHOLD = 4;

type LayerDrag = {
	tileId: string
	pointerId: number
	startY: number
	/** Where the layer was in the list when it was picked up, counting from the front */
	from: number
	rowHeight: number
	/** Whether it moved far enough to be dragging, rather than about to be clicked */
	moving: boolean
	dy: number
};

/**
 * Lists the screens of a custom layout while arranging, from the one in front to the one at the back. Dragging one
 * puts it in another layer, and clicking one selects it. Its buttons do the same with the keyboard, and remove it.
 */
function LayerList() {
	const {tiles, layout, activeTileId, setActiveTile, setLayer} = useTv();
	const [drag, setDrag] = useState<LayerDrag | null>(null);
	if (layout.kind !== 'custom') {
		return null;
	}
	const frontToBack = [...layout.stack].reverse();
	const last = frontToBack.length - 1;
	// Where the dragged layer would go, counting from the front
	const to = drag?.moving ? Math.max(0, Math.min(last, drag.from + Math.round(drag.dy / drag.rowHeight))) : null;

	function onPointerDown(event: PointerEvent<HTMLLIElement>, tileId: string, position: number) {
		// Its buttons are for clicking
		if (event.button !== 0 || (event.target as Element).closest('[data-layer-button]')) {
			return;
		}
		setDrag({
			tileId,
			pointerId: event.pointerId,
			startY: event.clientY,
			from: position,
			rowHeight: event.currentTarget.getBoundingClientRect().height,
			moving: false,
			dy: 0,
		});
	}

	function onPointerMove(event: PointerEvent<HTMLLIElement>) {
		if (!drag || drag.pointerId !== event.pointerId) {
			return;
		}
		if ((event.buttons & 1) === 0) {
			// Let go outside the list, before it started dragging
			setDrag(null);
			return;
		}
		const dy = event.clientY - drag.startY;
		if (!drag.moving && Math.abs(dy) < LAYER_DRAG_THRESHOLD) {
			return;
		}
		if (!drag.moving) {
			// Only captured once it's dragging, since capturing straight away would stop clicks from selecting it
			event.currentTarget.setPointerCapture(event.pointerId);
			setActiveTile(drag.tileId);
		}
		setDrag({...drag, moving: true, dy});
	}

	function onPointerUp(event: PointerEvent<HTMLLIElement>) {
		if (!drag || drag.pointerId !== event.pointerId) {
			return;
		}
		if (to !== null && to !== drag.from) {
			// The list goes from the front, the stack from the back
			setLayer(drag.tileId, last - to);
		}
		setDrag(null);
	}

	/**
	 * @return how far a layer is moved while another is dragged: the dragged one follows the pointer, and the ones it
	 * passes make room for it
	 */
	function offset(position: number): number {
		if (!drag?.moving || to === null) {
			return 0;
		}
		if (position === drag.from) {
			return drag.dy;
		}
		if (drag.from < position && position <= to) {
			return -drag.rowHeight;
		}
		if (to <= position && position < drag.from) {
			return drag.rowHeight;
		}
		return 0;
	}

	return <aside
		aria-label="Layers"
		className="absolute inset-x-0 bottom-0 z-20 flex h-72 flex-col border-t border-line bg-screen md:top-14 md:left-auto md:h-auto md:w-64 md:border-t-0 md:border-l"
	>
		<p className="flex items-baseline justify-between px-4 pt-3 pb-2 font-mono text-xs uppercase tracking-[0.2em] text-muted">
			Layers
			<span className="text-[0.625rem] tracking-[0.15em]">Front first</span>
		</p>
		<ol className="min-h-0 flex-1 overflow-y-auto">
			{frontToBack.map((tileId, position) => {
				const index = tiles.findIndex((tile) => tile.id === tileId);
				const dragged = drag?.moving === true && drag.tileId === tileId;
				return <LayerRow
					key={tileId}
					tile={tiles[index]}
					number={index + 1}
					selected={tileId === activeTileId}
					layer={last - position}
					front={position === 0}
					back={position === last}
					removable={tiles.length > 1}
					offset={offset(position)}
					dragged={dragged}
					// The others slide out of the way while dragging, but not when it's dropped and the list reorders
					sliding={drag?.moving === true && !dragged}
					onPointerDown={(event) => onPointerDown(event, tileId, position)}
					onPointerMove={onPointerMove}
					onPointerUp={onPointerUp}
					onPointerCancel={() => setDrag(null)}
				/>;
			})}
		</ol>
	</aside>;
}

type LayerRowProps = {
	tile: Tile
	number: number
	selected: boolean
	/** Its place in the stack, where 0 is behind all others */
	layer: number
	/** Whether it's in front of all other screens */
	front: boolean
	/** Whether it's behind all other screens */
	back: boolean
	removable: boolean
	/** How far it's moved from its place while dragging, in pixels */
	offset: number
	/** Whether it's the one being dragged */
	dragged: boolean
	/** Whether it slides to where it's moved */
	sliding: boolean
	onPointerDown: (event: PointerEvent<HTMLLIElement>) => void
	onPointerMove: (event: PointerEvent<HTMLLIElement>) => void
	onPointerUp: (event: PointerEvent<HTMLLIElement>) => void
	onPointerCancel: () => void
};

const layerButtonClassName = 'grid size-8 shrink-0 place-items-center font-mono text-sm text-fg hover:bg-fg hover:text-screen disabled:pointer-events-none '
	+ 'disabled:opacity-30 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent';

function LayerRow({tile, number, selected, layer, front, back, removable, offset, dragged, sliding, onPointerDown, onPointerMove, onPointerUp, onPointerCancel}: LayerRowProps) {
	const {setActiveTile, setLayer, removeScreen} = useTv();
	const channel = useChannel(tile.channelId);

	return <li
		onPointerDown={onPointerDown}
		onPointerMove={onPointerMove}
		onPointerUp={onPointerUp}
		onPointerCancel={onPointerCancel}
		style={{transform: offset ? `translateY(${offset}px)` : undefined}}
		className={`relative flex touch-none items-center gap-1 border-l-2 pr-2 ${dragged ? 'z-10 cursor-grabbing shadow-2xl' : 'cursor-grab'} ${sliding ? 'transition-transform duration-150 ease-out motion-reduce:transition-none' : ''} ${selected ? 'border-accent' : 'border-transparent'} ${selected || dragged ? 'bg-raised' : 'bg-screen'}`}
	>
		<button
			type="button"
			aria-pressed={selected}
			onClick={() => setActiveTile(tile.id)}
			className="flex min-w-0 flex-1 cursor-[inherit] items-baseline gap-3 py-2.5 pl-3.5 text-left focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
		>
			<span className={`font-mono text-sm tabular-nums ${selected ? 'text-accent' : 'text-muted'}`}>{number}</span>
			<span className={`truncate text-sm ${tile.channelId ? '' : 'text-muted'}`}>
				{tile.channelId ? channel?.name : 'Nothing on'}
				<span className="sr-only">, screen {number}</span>
			</span>
		</button>
		<button
			type="button"
			data-layer-button
			aria-label={`Bring screen ${number} forward`}
			title="Forward"
			disabled={front}
			onClick={() => setLayer(tile.id, layer + 1)}
			className={layerButtonClassName}
		>
			↑
		</button>
		<button
			type="button"
			data-layer-button
			aria-label={`Send screen ${number} back`}
			title="Back"
			disabled={back}
			onClick={() => setLayer(tile.id, layer - 1)}
			className={layerButtonClassName}
		>
			↓
		</button>
		{removable && <button
			type="button"
			data-layer-button
			aria-label={`Remove screen ${number}`}
			title="Remove"
			onClick={() => removeScreen(tile.id)}
			className={layerButtonClassName}
		>
			×
		</button>}
	</li>;
}

/**
 * Takes the place of the TV's controls while arranging the screens.
 */
function ArrangeBar({onDone}: {onDone: () => void}) {
	const {tiles, addScreen} = useTv();

	return <header className="absolute inset-x-0 top-0 z-20 flex h-14 items-center gap-6 bg-screen/80 px-4 sm:px-6">
		<p className="hidden font-mono text-xs uppercase tracking-[0.2em] sm:block">Arrange screens</p>
		<p className="hidden text-sm text-muted lg:block">Drag a screen to move it, or the corners of the selected one to resize it.</p>
		<div className="ml-auto flex gap-2">
			<button
				type="button"
				onClick={addScreen}
				disabled={tiles.length >= MAX_SCREENS}
				className={`${overlayButtonClassName} disabled:pointer-events-none disabled:opacity-40`}
			>
				Add screen
			</button>
			<button type="button" onClick={onDone} className={overlayButtonClassName}>Done</button>
		</div>
	</header>;
}

const overlayButtonClassName = 'border border-fg/70 px-3 py-2 font-mono text-xs uppercase tracking-[0.2em] text-fg transition-colors '
	+ 'hover:bg-fg hover:text-screen focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent';

type TvOverlayProps = {
	user: User
	visible: boolean
	pickingMultiview: boolean
	onPickingMultiviewChange: (open: boolean) => void
	/** Starts moving and resizing the screens */
	onArrange: () => void
};

function TvOverlay({user, visible, pickingMultiview, onPickingMultiviewChange, onArrange}: TvOverlayProps) {
	const fullscreen = useFullscreenElement() === document.documentElement;

	return <header
		className={`absolute inset-x-0 top-0 z-20 flex flex-wrap items-center gap-x-6 gap-y-3 bg-screen/80 px-4 py-3 transition-opacity duration-200 sm:px-6 ${visible ? 'opacity-100' : 'pointer-events-none opacity-0'}`}
		inert={!visible}
	>
		<Brand/>

		<MultiviewPicker className={overlayButtonClassName} open={pickingMultiview} onOpenChange={onPickingMultiviewChange} onArrange={onArrange}/>

		<nav className="flex flex-wrap gap-2 sm:ml-auto">
			<Link to="/channels" className={overlayButtonClassName}>Channels</Link>
			<Link to="/guide" className={overlayButtonClassName}>Guide</Link>
			{hasPrivilege(user, 'admin') && <Link to="/admin" className={overlayButtonClassName}>Admin</Link>}
			<Link to="/account" className={overlayButtonClassName}>{user.displayName}</Link>
			{canFullscreenPage() && <button
				type="button"
				aria-label={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
				title={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
				onClick={() => toggleFullscreen(document.documentElement)}
				// Less padding than the text buttons, so the icon makes it just as tall
				className={overlayButtonClassName.replace('px-3 py-2', 'p-1.5')}
			>
				{fullscreen ? <ExitFullscreenIcon/> : <FullscreenIcon/>}
			</button>}
		</nav>
	</header>;
}

/**
 * Tracks whether the user is doing something on the TV: moving the mouse, touching the screen or using the keyboard.
 * After a few seconds of nothing, the TV is no longer awake and hides its controls. It stays awake while the keyboard
 * focus is on one of its controls, so they don't disappear from under keyboard users.
 *
 * @return whether the TV is awake, and a function that wakes it up
 */
function useAwake(element: RefObject<HTMLElement | null>): [boolean, () => void] {
	const [awake, setAwake] = useState(true);
	const timeout = useRef<number | undefined>(undefined);

	const wake = useCallback(() => {
		setAwake(true);
		window.clearTimeout(timeout.current);
		const sleep = () => {
			const focused = document.activeElement;
			if (focused instanceof HTMLElement && element.current?.contains(focused) && focused.matches(':focus-visible')) {
				timeout.current = window.setTimeout(sleep, CONTROLS_TIMEOUT);
			} else {
				setAwake(false);
			}
		};
		timeout.current = window.setTimeout(sleep, CONTROLS_TIMEOUT);
	}, [element]);

	useEffect(() => {
		wake();
		return () => window.clearTimeout(timeout.current);
	}, [wake]);

	return [awake, wake];
}
