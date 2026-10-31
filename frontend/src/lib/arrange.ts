import {clampFrame, EDGE_MARGIN, type Frame, MIN_FRAME_SIZE, TILE_ASPECT} from './tvLayout';

/** How close an edge has to come to something to snap to it, in pixels */
const SNAP_DISTANCE = 12;

/** The corner of a screen that's dragged to resize it */
export type Corner = 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';

/** The size of the TV in pixels, to snap in pixels rather than in shares of it */
export type Canvas = {width: number, height: number};

/**
 * @return the frame moved by a share of the TV's width and height, with its edges or middle snapped to the TV's edges
 * and middle, and to the edges of the other screens
 */
export function moveFrame(start: Frame, dx: number, dy: number, others: Frame[], canvas: Canvas): Frame {
	const x = snapPosition(start.x + dx, start.size, xTargets(others), canvas.width);
	const y = snapPosition(start.y + dy, start.size, yTargets(others), canvas.height);
	return clampFrame({x, y, size: start.size});
}

/**
 * Resizes a screen by dragging one of its corners, while the opposite corner stays where it is. Screens stay 16:9, so
 * whichever way the corner moved most decides the size, and the corner snaps to what's near it.
 *
 * @param dx how far the corner moved, as a share of the TV's width
 * @param dy how far the corner moved, as a share of the TV's height
 */
export function resizeFrame(start: Frame, corner: Corner, dx: number, dy: number, others: Frame[], canvas: Canvas): Frame {
	const right = corner.endsWith('right');
	const bottom = corner.startsWith('bottom');
	// The corner that stays put
	const anchorX = right ? start.x : start.x + start.size;
	const anchorY = bottom ? start.y : start.y + start.size;

	// Growing is away from the anchor
	const growX = right ? dx : -dx;
	const growY = bottom ? dy : -dy;
	// Compared in pixels, since a share of the width is more pixels than a share of the height
	let size = start.size + (Math.abs(growX * canvas.width) >= Math.abs(growY * canvas.height) ? growX : growY);

	// The size each snap of the moving corner would give, of which the closest one wins
	let closest = SNAP_DISTANCE;
	let snapped = size;
	const edgeX = right ? anchorX + size : anchorX - size;
	for (const target of xTargets(others)) {
		const distance = Math.abs(target - edgeX) * canvas.width;
		if (distance < closest) {
			closest = distance;
			snapped = Math.abs(target - anchorX);
		}
	}
	const edgeY = bottom ? anchorY + size : anchorY - size;
	for (const target of yTargets(others)) {
		const distance = Math.abs(target - edgeY) * canvas.height;
		if (distance < closest) {
			closest = distance;
			snapped = Math.abs(target - anchorY);
		}
	}
	size = snapped;

	// No further than the edges of the TV from the anchor
	const room = Math.min(right ? 1 - anchorX : anchorX, bottom ? 1 - anchorY : anchorY);
	size = Math.min(room, Math.max(MIN_FRAME_SIZE, size));
	return {x: right ? anchorX : anchorX - size, y: bottom ? anchorY : anchorY - size, size};
}

/**
 * @return the frame moved or resized with the keyboard, with steps of a hundredth of the TV. Resizing keeps the top
 * left corner where it is.
 */
export function nudgeFrame(frame: Frame, key: string, resize: boolean): Frame | null {
	const step = 0.01;
	const dx = key === 'ArrowLeft' ? -step : key === 'ArrowRight' ? step : 0;
	const dy = key === 'ArrowUp' ? -step : key === 'ArrowDown' ? step : 0;
	if (dx === 0 && dy === 0) {
		return null;
	}
	if (resize) {
		// Right and down make it bigger
		const size = Math.min(frame.size + dx + dy, 1 - frame.x, 1 - frame.y);
		return clampFrame({...frame, size});
	}
	return clampFrame({...frame, x: frame.x + dx, y: frame.y + dy});
}

/**
 * @return where a screen's left (or top) edge goes after snapping its left edge, right edge or middle to one of the
 * targets, whichever is closest
 */
function snapPosition(position: number, size: number, targets: number[], pixels: number): number {
	let closest = SNAP_DISTANCE;
	let snapped = position;
	for (const target of targets) {
		for (const [offset, matches] of [[0, true], [size, true], [size / 2, target === 0.5]] as const) {
			const distance = Math.abs(target - (position + offset)) * pixels;
			if (matches && distance < closest) {
				closest = distance;
				snapped = target - offset;
			}
		}
	}
	return snapped;
}

/**
 * @return where edges snap to across: the TV's edges, a little inside them, its middle, and the other screens' edges
 */
function xTargets(others: Frame[]): number[] {
	return [0, EDGE_MARGIN, 0.5, 1 - EDGE_MARGIN, 1, ...others.flatMap((other) => [other.x, other.x + other.size])];
}

/**
 * @return where edges snap to from top to bottom. The margin is as many pixels as across, which is a larger share of
 * the TV's height.
 */
function yTargets(others: Frame[]): number[] {
	const margin = EDGE_MARGIN * TILE_ASPECT;
	return [0, margin, 0.5, 1 - margin, 1, ...others.flatMap((other) => [other.y, other.y + other.size])];
}
