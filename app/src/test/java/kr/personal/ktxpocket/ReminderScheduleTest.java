package kr.personal.ktxpocket;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReminderScheduleTest {
    @Test public void remindersFollowSuccessAndPrecedePaymentDeadline() {
        long now = 1_000_000;
        assertArrayEquals(new long[]{now + 60_000, now + 5 * 60_000}, ReminderSchedule.paymentTimes(now, now + 10 * 60_000));
    }

    @Test public void shortDeadlineDoesNotProduceDuplicatesOrAnAlarmAfterExpiry() {
        long now = 1_000_000;
        assertArrayEquals(new long[]{now + 60_000}, ReminderSchedule.paymentTimes(now, now + 4 * 60_000));
        assertArrayEquals(new long[]{now + 29_000}, ReminderSchedule.paymentTimes(now, now + 30_000));
    }

    @Test public void unknownDeadlineOnlySchedulesTheFollowUp() {
        assertArrayEquals(new long[]{61_000}, ReminderSchedule.paymentTimes(1000, 0));
    }

    @Test public void expiredDeadlinesNeverScheduleFuturePaymentWarnings() {
        assertArrayEquals(new long[0], ReminderSchedule.paymentTimes(100_000, 90_000));
        assertArrayEquals(new long[0], ReminderSchedule.paymentTimes(100_000, 101_000));
    }

    @Test public void interruptionRequiresAnActiveWatchAndAMissingProcessWithAnOldHeartbeat() {
        assertTrue(ReminderSchedule.interrupted(true, false, 200_000, 100_000));
        assertTrue(ReminderSchedule.interrupted(true, false, 10_000, 100_000));
        assertFalse(ReminderSchedule.interrupted(true, true, 200_000, 100_000));
        assertFalse(ReminderSchedule.interrupted(false, false, 200_000, 100_000));
        assertFalse(ReminderSchedule.interrupted(true, false, 190_000, 100_000));
    }
}
