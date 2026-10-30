package nl.noahvanderaa.kijkbuis.watchhistory.util;

public final class TimeUtil {

	private TimeUtil() {
	}

	public static String formatDuration(long ms) {
		long seconds = ms / 1000;
		long minutes = seconds / 60;
		long hours = minutes / 60;
		long days = hours / 24;

		StringBuilder sb = new StringBuilder();
		if (days > 0) {
			sb.append(days).append(" day");
			if (days > 1) {
				sb.append('s');
			}
			sb.append(' ');
		}

		if(hours > 0) {
			sb.append(hours % 24).append(" hour");
			if (hours % 24 != 1) {
				sb.append('s');
			}
			sb.append(' ');
		}

		if (minutes > 0) {
			sb.append(minutes % 60).append(" minute");
			if (minutes % 60 != 1) {
				sb.append('s');
			}
			sb.append(' ');
		}

		if (seconds > 0) {
			sb.append(seconds % 60).append(" second");
			if (seconds % 60 != 1) {
				sb.append('s');
			}
		}

		return sb.toString().trim();
	}

}
