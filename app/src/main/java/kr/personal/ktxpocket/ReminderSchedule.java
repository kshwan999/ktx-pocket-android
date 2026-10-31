package kr.personal.ktxpocket;

final class ReminderSchedule {
    static long[] paymentTimes(long now, long deadline) {
        if (deadline > 0 && deadline <= now + 1000) return new long[0];
        long first = deadline > 0 ? Math.min(now + 60_000, deadline - 1000) : now + 60_000;
        long warning = deadline - 5 * 60_000;
        return warning > first + 60_000 ? new long[]{first, warning} : new long[]{first};
    }

    static boolean interrupted(boolean watching, boolean running, long elapsed, long heartbeat) {
        long age = elapsed - heartbeat;
        return watching && !running && (age > 90_000 || age < 0);
    }
}
