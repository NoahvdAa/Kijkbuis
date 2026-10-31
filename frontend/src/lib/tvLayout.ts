import {useEffect, useState} from 'react';

/** Every tile shows a 16:9 picture */
export const TILE_ASPECT = 16 / 9;

/**
 * A multiview grid of screens.
 */
export type Grid = {
    columns: number
    rows: number
};

/** The most screens the TV shows at once, in any layout */
export const MAX_SCREENS = 6;
/** The smallest a screen can be in a custom layout, as a share of the TV's width */
export const MIN_FRAME_SIZE = 0.15;
/**
 * The space left between a screen and the edge of the TV it's snapped to, as a share of the TV's width. It's the same
 * number of pixels from the top and bottom, which is a larger share of the TV's height.
 */
export const EDGE_MARGIN = 0.025;

/**
 * Where a screen is in a custom layout, from the TV's top left. The TV is 16:9 then, like the screens, so the screen
 * takes the same share of the TV's height as of its width.
 */
export type Frame = {
    x: number
    y: number
    /** The share of the TV's width (and height) the screen takes */
    size: number
};

/**
 * Where a screen is on the TV, as shares of its width and height, from its top left.
 */
export type Rect = {
    x: number
    y: number
    width: number
    height: number
};

/**
 * @return where each screen of a grid goes, from left to right and top to bottom
 */
export function gridRects(grid: Grid): Rect[] {
    const rects: Rect[] = [];
    for (let row = 0; row < grid.rows; row++) {
        for (let column = 0; column < grid.columns; column++) {
            rects.push({x: column / grid.columns, y: row / grid.rows, width: 1 / grid.columns, height: 1 / grid.rows});
        }
    }
    return rects;
}

/**
 * @return the screens of a grid as a custom layout. Grids that aren't 16:9 as a whole are made smaller to fit, in the
 * middle of the TV.
 */
export function gridFrames(grid: Grid): Frame[] {
    const size = 1 / Math.max(grid.columns, grid.rows);
    const left = (1 - grid.columns * size) / 2;
    const top = (1 - grid.rows * size) / 2;
    return gridRects(grid).map((_, index) => ({
        x: left + index % grid.columns * size,
        y: top + Math.floor(index / grid.columns) * size,
        size,
    }));
}

/**
 * @return a full screen, with a small one in the top right corner. Not the bottom, where it would cover the big
 * screen's controls.
 */
export function pictureInPictureFrames(): Frame[] {
    const size = 0.3;
    return [
        {x: 0, y: 0, size: 1},
        {x: 1 - size - EDGE_MARGIN, y: EDGE_MARGIN * TILE_ASPECT, size},
    ];
}

/**
 * @return where a screen added to a custom layout goes: in the top right corner, or a bit lower and further left
 * than the screens already there
 */
export function newFrame(existing: Frame[]): Frame {
    const size = 1 / 3;
    const step = 0.05;
    let frame = {x: 1 - size - EDGE_MARGIN, y: EDGE_MARGIN * TILE_ASPECT, size};
    while (existing.some((other) => Math.abs(other.x - frame.x) < step / 2 && Math.abs(other.y - frame.y) < step / 2)
    && frame.x - step >= 0 && frame.y + size + step <= 1) {
        frame = {x: frame.x - step, y: frame.y + step, size};
    }
    return frame;
}

/**
 * @return the frame made to fit: no smaller than the minimum, and entirely on the TV
 */
export function clampFrame(frame: Frame): Frame {
    const size = Math.min(1, Math.max(MIN_FRAME_SIZE, frame.size));
    return {
        x: Math.min(1 - size, Math.max(0, frame.x)),
        y: Math.min(1 - size, Math.max(0, frame.y)),
        size,
    };
}

/**
 * @return the largest grid multiview offers: wider on landscape screens, taller on portrait ones
 */
export function maxGrid(landscape: boolean): Grid {
    return landscape ? {columns: 3, rows: 2} : {columns: 2, rows: 3};
}

/**
 * @return whether the window is wider than it's tall, updated when it's resized or the device is rotated
 */
export function useLandscape(): boolean {
    const [landscape, setLandscape] = useState(() => window.innerWidth >= window.innerHeight);

    useEffect(() => {
        const onResize = () => setLandscape(window.innerWidth >= window.innerHeight);
        window.addEventListener('resize', onResize);
        return () => window.removeEventListener('resize', onResize);
    }, []);

    return landscape;
}

/**
 * @return the shape of a grid as a whole, as its width divided by its height
 */
export function gridAspect(grid: Grid): number {
    return grid.columns * TILE_ASPECT / grid.rows;
}

/**
 * Publishes the shape of the TV's picture as the --tv-aspect CSS variable, which the PiP and the menus' headers are
 * sized by.
 */
export function useTvAspect(aspect: number): void {
    useEffect(() => {
        document.documentElement.style.setProperty('--tv-aspect', String(aspect));
        return () => {
            document.documentElement.style.removeProperty('--tv-aspect');
        };
    }, [aspect]);
}
