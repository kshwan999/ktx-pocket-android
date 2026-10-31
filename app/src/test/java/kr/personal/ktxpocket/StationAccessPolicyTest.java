package kr.personal.ktxpocket;

import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class StationAccessPolicyTest {
    private LinkedHashMap<String, Integer> times(int seoul, int suseo, int gwangmyeong) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        result.put("서울", seoul); result.put("수서", suseo); result.put("광명", gwangmyeong); return result;
    }

    @Test public void extraTravelLimitIsInclusiveAndRejectsSlowerStations() {
        StationAccessPolicy policy = new StationAccessPolicy(times(20, 50, 51), "서울", 30, 15);
        assertEquals(Arrays.asList("서울", "수서"), policy.eligibleStations());
    }

    @Test public void fasterAlternativesAreAllowedWithoutExtraTravel() {
        StationAccessPolicy policy = new StationAccessPolicy(times(20, 10, 25), "서울", 0, 15);
        assertEquals(Arrays.asList("서울", "수서"), policy.eligibleStations());
    }

    @Test public void baselineRemainsFirstAndOtherCandidatesFollowTravelTime() {
        StationAccessPolicy policy = new StationAccessPolicy(times(20, 50, 30), "서울", 40, 15);
        assertEquals(Arrays.asList("서울", "광명", "수서"), policy.eligibleStations());
    }

    @Test public void missingBaselineAndUnregisteredStationsFailInsteadOfAssumingZeroMinutes() {
        assertThrows(IllegalArgumentException.class, () -> new StationAccessPolicy(times(20, 50, 30), "용산", 30, 15));
        StationAccessPolicy policy = new StationAccessPolicy(times(20, 50, 30), "서울", 30, 15);
        assertThrows(IllegalArgumentException.class, () -> policy.minutesFor("용산"));
    }

    @Test public void unreasonableAndNegativeMinutesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StationAccessPolicy(times(-1, 50, 30), "서울", 30, 15));
        assertThrows(IllegalArgumentException.class, () -> new StationAccessPolicy(times(20, 361, 30), "서울", 30, 15));
        assertThrows(IllegalArgumentException.class, () -> new StationAccessPolicy(times(20, 50, 30), "서울", -1, 15));
        assertThrows(IllegalArgumentException.class, () -> new StationAccessPolicy(times(20, 50, 30), "서울", 30, 121));
    }
}
