import {useCallback, useEffect, useRef, useState} from 'react';
import {useDismiss} from '#/lib/dismiss';
import {hasFrames, type Layout, type SavedLayout, useTv} from '#/lib/tv';
import {type Frame, type Grid, pictureInPictureFrames} from '#/lib/tvLayout';

type MultiviewPickerProps = {
	className: string
	open: boolean
	onOpenChange: (open: boolean) => void
	/** Starts moving and resizing the screens */
	onArrange: () => void
};

const customButtonClassName = 'flex items-center gap-3 whitespace-nowrap border-l-2 px-2 py-1.5 text-left font-mono text-xs uppercase tracking-[0.15em] '
	+ 'hover:bg-raised focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent';
/** Marks the custom layout the TV has now, like the grid cells do */
const currentClassName = 'border-accent bg-raised text-accent';

/**
 * Picks how the screens are arranged. Hovering a cell previews a grid that size, and clicking it switches to that
 * grid. Below that are the custom layouts, where screens can be anywhere, such as a small one on top of a big one.
 */
export function MultiviewPicker({className, open, onOpenChange, onArrange}: MultiviewPickerProps) {
	const {tiles, layout, maxGrid, savedLayout, setGrid, setFrames} = useTv();
	const [hovered, setHovered] = useState<Grid | null>(null);
	const container = useRef<HTMLDivElement>(null);
	const grid = layout.kind === 'grid' ? layout.grid : null;
	const preview = hovered ?? grid;
	const pictureInPicture = pictureInPictureFrames();
	const showingPictureInPicture = hasFrames(layout, tiles, pictureInPicture);
	// Only one is marked, if my layout happens to be a picture in picture
	const showingMyLayout = !showingPictureInPicture && savedLayout !== null && hasFrames(layout, tiles, savedLayout.frames, savedLayout.stack);

	const close = useCallback(() => onOpenChange(false), [onOpenChange]);
	useDismiss(open, container, close);

	useEffect(() => {
		if (!open) {
			setHovered(null);
		}
	}, [open]);

	const cells: Grid[] = [];
	for (let row = 1; row <= maxGrid.rows; row++) {
		for (let column = 1; column <= maxGrid.columns; column++) {
			cells.push({columns: column, rows: row});
		}
	}

	function pickFrames(frames: Frame[], stack?: number[]) {
		setFrames(frames, stack);
		onOpenChange(false);
	}

	return <div ref={container} className="relative">
		<button type="button" aria-expanded={open} aria-haspopup="dialog" onClick={() => onOpenChange(!open)} className={`flex items-center gap-2.5 ${className}`}>
			<LayoutIcon layout={layout} order={tiles.map((tile) => tile.id)}/>
			Multiview
		</button>

		{open && <div role="dialog" aria-label="Multiview" className="absolute top-full left-0 z-30 mt-2 grid gap-3 border border-line bg-panel p-4 shadow-2xl">
			<p className="font-mono text-xs uppercase tracking-[0.2em] text-muted">
				{preview
					? `${preview.columns} × ${preview.rows}`
					: showingPictureInPicture ? 'Picture in picture' : showingMyLayout ? 'My layout' : 'Custom'}
			</p>
			<div
				className="grid gap-1"
				style={{gridTemplateColumns: `repeat(${maxGrid.columns}, auto)`}}
				onPointerLeave={() => setHovered(null)}
			>
				{cells.map((cell) => {
					const inPreview = preview !== null && cell.columns <= preview.columns && cell.rows <= preview.rows;
					const current = grid !== null && cell.columns === grid.columns && cell.rows === grid.rows;
					return <button
						key={`${cell.columns}x${cell.rows}`}
						type="button"
						aria-label={`${cell.columns} by ${cell.rows}`}
						aria-pressed={current}
						onPointerEnter={() => setHovered(cell)}
						onFocus={() => setHovered(cell)}
						onClick={() => {
							setGrid(cell);
							onOpenChange(false);
						}}
						className={`aspect-video w-14 border transition-colors focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-accent ${inPreview ? 'border-fg bg-fg' : 'border-fg/40 bg-transparent'}`}
					/>;
				})}
			</div>

			<div className="-mx-2 grid border-t border-line pt-2">
				<button
					type="button"
					aria-pressed={showingPictureInPicture}
					onClick={() => pickFrames(pictureInPicture)}
					className={`${customButtonClassName} ${showingPictureInPicture ? currentClassName : 'border-transparent'}`}
				>
					<FramesIcon frames={pictureInPicture}/>
					Picture in picture
				</button>
				{savedLayout && <button
					type="button"
					aria-pressed={showingMyLayout}
					onClick={() => pickFrames(savedLayout.frames, savedLayout.stack)}
					className={`${customButtonClassName} ${showingMyLayout ? currentClassName : 'border-transparent'}`}
				>
					<FramesIcon frames={stacked(savedLayout)}/>
					My layout
				</button>}
				<button type="button" onClick={onArrange} className={`${customButtonClassName} border-transparent`}>
					<span aria-hidden="true" className="w-4 text-center">↔</span>
					Arrange screens
				</button>
			</div>
		</div>}
	</div>;
}

/**
 * A small picture of the layout the TV has now.
 *
 * @param order the screens' IDs in the order of their numbers
 */
function LayoutIcon({layout, order}: {layout: Layout, order: string[]}) {
	if (layout.kind === 'custom') {
		return <FramesIcon frames={layout.stack.map((id) => layout.frames[id])}/>;
	}
	const {grid} = layout;
	return <span aria-hidden="true" className="grid size-3.5 gap-px" style={{gridTemplateColumns: `repeat(${grid.columns}, 1fr)`}}>
		{order.map((id) => <span key={id} className="bg-current"/>)}
	</span>;
}

/**
 * A small picture of a custom layout, with the screens as outlines so the ones on top stand out.
 *
 * @param frames the screens, from the bottom up
 */
function FramesIcon({frames}: {frames: Frame[]}) {
	return <span aria-hidden="true" className="relative aspect-video w-4 shrink-0">
		{frames.map((frame, index) => <span
			key={index}
			className="absolute border border-current"
			style={{left: `${frame.x * 100}%`, top: `${frame.y * 100}%`, width: `${frame.size * 100}%`, height: `${frame.size * 100}%`}}
		/>)}
	</span>;
}

/**
 * @return the frames of a saved layout from the bottom up
 */
function stacked(saved: SavedLayout): Frame[] {
	return saved.stack.map((index) => saved.frames[index]);
}
