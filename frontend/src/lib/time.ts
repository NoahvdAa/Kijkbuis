import {useEffect, useState} from 'react';
import type {Programme} from './types';

export const MINUTE = 60_000;
export const HOUR = 60 * MINUTE;

export function formatTime(time: string | number | Date): string {
	return new Date(time).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
}

/**
 * @return how long ago something happened, such as "just now", "5 min ago" or "3 days ago"
 */
export function formatAgo(time: string | number, now: number = Date.now()): string {
	const elapsed = now - new Date(time).getTime();
	if (elapsed < MINUTE) {
		return 'just now';
	}
	return `${formatDuration(elapsed)} ago`;
}

/**
 * @return how long something takes, roughly, such as "5 min", "2 h" or "3 days"
 */
export function formatDuration(ms: number): string {
	if (ms < HOUR) {
		return `${Math.max(1, Math.floor(ms / MINUTE))} min`;
	}
	if (ms < 24 * HOUR) {
		return `${Math.floor(ms / HOUR)} h`;
	}
	const days = Math.floor(ms / (24 * HOUR));
	return days === 1 ? '1 day' : `${days} days`;
}

/**
 * @return the date and time, such as "27 Oct, 14:05"
 */
export function formatDateTime(time: string | number): string {
	return new Date(time).toLocaleString([], {day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit'});
}

/**
 * @return Today, Tomorrow or Yesterday, or else the weekday and date
 */
export function formatDay(time: number, now: number = Date.now()): string {
	const days = Math.round((startOfDay(time) - startOfDay(now)) / (24 * HOUR));
	if (days === 0) {
		return 'Today';
	}
	if (days === 1) {
		return 'Tomorrow';
	}
	if (days === -1) {
		return 'Yesterday';
	}
	return new Date(time).toLocaleDateString([], {weekday: 'short', day: 'numeric', month: 'short'});
}

export function startOfDay(time: number): number {
	const date = new Date(time);
	date.setHours(0, 0, 0, 0);
	return date.getTime();
}

export function floorToHalfHour(time: number): number {
	return Math.floor(time / (30 * MINUTE)) * 30 * MINUTE;
}

/**
 * @return how far along the programme is, from 0 to 1
 */
export function progressOf(programme: Programme, now: number): number {
	const start = Date.parse(programme.startsAt);
	const end = Date.parse(programme.endsAt);
	return Math.min(Math.max((now - start) / (end - start), 0), 1);
}

/**
 * The current time, updated every interval.
 */
export function useNow(intervalMs: number = 30_000): number {
	const [now, setNow] = useState(Date.now);

	useEffect(() => {
		const interval = window.setInterval(() => setNow(Date.now()), intervalMs);
		return () => window.clearInterval(interval);
	}, [intervalMs]);

	return now;
}
