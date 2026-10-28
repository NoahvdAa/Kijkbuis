import {useCallback, useEffect, useRef, useState} from 'react';
import {useDismiss} from '#/lib/dismiss';
import {useTv} from '#/lib/tv';
import type {Grid} from '#/lib/tvLayout';

type MultiviewPickerProps = {
	className: string
	open: boolean
	onOpenChange: (open: boolean) => void
};

/**
 * Picks the multiview grid: hovering a cell previews a grid that size, clicking it switches to that grid.
 */
export function MultiviewPicker({className, open, onOpenChange}: MultiviewPickerProps) {
	const {grid, maxGrid, setGrid} = useTv();
	const [hovered, setHovered] = useState<Grid | null>(null);
	const container = useRef<HTMLDivElement>(null);
	const preview = hovered ?? grid;

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

	return <div ref={container} className="relative">
		<button type="button" aria-expanded={open} aria-haspopup="dialog" onClick={() => onOpenChange(!open)} className={`flex items-center gap-2.5 ${className}`}>
			<GridIcon grid={grid}/>
			Multiview
		</button>

		{open && <div role="dialog" aria-label="Multiview" className="absolute top-full left-0 z-30 mt-2 grid gap-3 border border-line bg-panel p-4 shadow-2xl">
			<p className="font-mono text-xs uppercase tracking-[0.2em] text-muted">
				{preview.columns} × {preview.rows}
			</p>
			<div
				className="grid gap-1"
				style={{gridTemplateColumns: `repeat(${maxGrid.columns}, auto)`}}
				onPointerLeave={() => setHovered(null)}
			>
				{cells.map((cell) => {
					const inPreview = cell.columns <= preview.columns && cell.rows <= preview.rows;
					const current = cell.columns === grid.columns && cell.rows === grid.rows;
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
		</div>}
	</div>;
}

/**
 * A small picture of a grid.
 */
function GridIcon({grid}: {grid: Grid}) {
	return <span aria-hidden="true" className="grid size-3.5 gap-px" style={{gridTemplateColumns: `repeat(${grid.columns}, 1fr)`}}>
		{Array.from({length: grid.columns * grid.rows}, (_, i) => <span key={i} className="bg-current"/>)}
	</span>;
}
