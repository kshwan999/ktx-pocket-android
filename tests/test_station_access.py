import copy
import json
from datetime import datetime, timedelta
from pathlib import Path
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).parents[1] / "app/src/main/python"))
import engine
import android_transport as transport
import test_engine as engine_tests


class StationAccessTests(unittest.TestCase):
    def setUp(self):
        self.access = dict(baseline="서울", stations=[dict(name="서울", minutes=20), dict(name="수서", minutes=50)],
                           extra_minutes=30, boarding_minutes=15)
        self.config = dict(username="test-user", password="not-a-real-password", departure="수서", arrival="부산",
                           date="20991101", start="1630", end="1750", passengers=1, option="GENERAL_ONLY",
                           confirmed=True, station_access=self.access)
        self.now = datetime(2099, 11, 1, 16, tzinfo=engine.KST)

    def test_budget_is_inclusive_and_only_registered_stations_are_allowed(self):
        plans = engine.validated_conditions(self.config, self.now)
        self.assertEqual(plans[0][0]["access_minutes"], 50)
        self.assertEqual(plans[0][0]["boarding_minutes"], 15)
        for departure, extra in (("수서", 29), ("용산", 30)):
            with self.subTest(departure=departure), self.assertRaises(ValueError):
                engine.validated_conditions({**self.config, "departure": departure,
                    "station_access": {**self.access, "extra_minutes": extra}}, self.now)

    def test_malformed_profiles_fail_before_search(self):
        invalid = [[], {}, {**self.access, "baseline": []}, {**self.access, "baseline": "용산"},
                   {**self.access, "stations": []}, {**self.access, "stations": self.access["stations"] * 2},
                   {**self.access, "stations": [dict(name="서울", minutes=20), dict(name=" 서울 ", minutes=30)]}]
        for field, values in (("extra_minutes", [-1, 361, True, "30"]), ("boarding_minutes", [-1, 121, False])):
            invalid.extend({**self.access, field: value} for value in values)
        invalid.extend({**self.access, "stations": [dict(name="서울", minutes=value)]} for value in [-1, 361, True, "20"])
        for value in invalid:
            with self.subTest(value=value), self.assertRaises(ValueError): engine.validated_access(value)
        self.assertIsNone(engine.validated_access(None))

    def test_earliest_departure_rolls_over_midnight_and_honors_later_start(self):
        condition = dict(access_minutes=50, boarding_minutes=15)
        now = datetime(2099, 11, 1, 23, 30, tzinfo=engine.KST)
        start = now - timedelta(hours=1)
        self.assertEqual(engine.earliest_departure(condition, start, now),
                         datetime(2099, 11, 2, 0, 35, 5, tzinfo=engine.KST))
        future = now + timedelta(hours=4)
        self.assertEqual(engine.earliest_departure(condition, future, now), future)

    def outcomes(self, outcomes):
        # Reuse the existing fake network/clock harness without making a real login.
        harness = engine_tests.EngineTests()
        harness.config = copy.deepcopy(self.config)
        return harness.run_search_outcomes(outcomes)

    def test_query_starts_after_access_and_boarding_time_and_success_names_departure(self):
        train = SimpleNamespace(dep_date="20991101", dep_time="171000", has_general_seat=lambda: True)
        with patch("engine.datetime", wraps=datetime) as dates:
            dates.now.return_value = self.now
            client, listener, _, _ = self.outcomes([[train]])
        self.assertEqual(client.trains.search.call_args.kwargs["depart_after"], self.now + timedelta(minutes=65, seconds=5))
        client.reservations.create.assert_called_once()
        self.assertEqual(listener.onStatus.call_args.args[0], "success")
        self.assertIn("수서", listener.onStatus.call_args.args[1])
        self.assertIn("16:05", listener.onStatus.call_args.args[1])

    def test_time_spent_searching_is_rechecked_before_reservation(self):
        train = SimpleNamespace(dep_date="20991101", dep_time="171000", has_general_seat=lambda: True)
        later = self.now + timedelta(minutes=10)
        expired = self.now + timedelta(hours=2)
        with patch("engine.datetime", wraps=datetime) as dates:
            dates.now.side_effect = [self.now, self.now, later, expired]
            client, listener, _, _ = self.outcomes([[train]])
        client.reservations.create.assert_not_called()
        client.trains.search.assert_called_once()
        self.assertEqual(listener.onStatus.call_args.args[0], "stopped")

    def test_no_query_when_all_windows_cannot_be_reached(self):
        with patch("engine.datetime", wraps=datetime) as dates:
            dates.now.return_value = self.now + timedelta(minutes=46)
            client, listener, _, _ = self.outcomes([])
        client.trains.search.assert_not_called()
        client.reservations.create.assert_not_called()
        self.assertEqual(listener.onStatus.call_args.args[0], "stopped")

    def test_closed_booking_window_skips_safely_instead_of_reporting_unknown_reservation(self):
        train = SimpleNamespace(dep_date="20991101", dep_time="171000", has_general_seat=lambda: True)
        later_train = SimpleNamespace(dep_date="20991101", dep_time="173000", has_general_seat=lambda: True)
        with patch("engine.datetime", wraps=datetime) as dates, patch("android_transport.reservation_window") as window:
            dates.now.return_value = self.now
            window.return_value.__enter__.side_effect = [transport.ReservationWindowClosed(), None]
            client, listener, _, _ = self.outcomes([[train, later_train]])
        client.reservations.create.assert_called_once()
        self.assertIs(client.reservations.create.call_args.args[0], later_train)
        self.assertEqual(listener.onStatus.call_args.args[0], "success")


class ReservationGuardTests(unittest.TestCase):
    def setUp(self):
        self.session = object.__new__(transport.AndroidSession)
        self.session.headers = {}
        self.session.native = Mock()
        self.session.native.exchange.return_value = json.dumps(dict(status_code=200, text="{}"))

    def test_gate_delay_can_close_window_before_any_booking_request_is_sent(self):
        from pykorail.constants import API_ENDPOINTS
        wall = [100.0]
        with patch.object(transport, "_last_finished", 100), \
             patch.object(transport.time, "monotonic", return_value=100), \
             patch.object(transport.time, "time", side_effect=lambda: wall[0]), \
             patch.object(transport.random, "uniform", return_value=3), \
             patch.object(transport.time, "sleep", side_effect=lambda seconds: wall.__setitem__(0, wall[0] + seconds)), \
             transport.reservation_window(102, lambda: False), self.assertRaises(transport.ReservationWindowClosed):
            self.session.get(API_ENDPOINTS["reserve"], params={"test": "value"})
        self.session.native.exchange.assert_not_called()
        self.assertIsNone(transport._reservation_guard)

    def test_stop_blocks_booking_but_guard_does_not_block_signing_or_result_queries(self):
        from pykorail.constants import API_ENDPOINTS
        with patch.object(transport.time, "sleep"), patch.object(transport.time, "time", return_value=100), \
             transport.reservation_window(200, lambda: True):
            self.session.get("https://example.invalid/sign")
            with self.assertRaises(transport.ReservationWindowClosed): self.session.get(API_ENDPOINTS["reserve"])
        self.session.native.exchange.assert_called_once()
        self.assertIsNone(transport._reservation_guard)

    def test_reachable_booking_is_sent_and_network_error_still_propagates_as_transport_error(self):
        from pykorail.constants import API_ENDPOINTS
        from pykorail.exceptions import TransportError
        with patch.object(transport.time, "sleep"), patch.object(transport.time, "time", return_value=100), \
             transport.reservation_window(200, lambda: False):
            self.assertEqual(self.session.get(API_ENDPOINTS["reserve"]).status_code, 200)
            self.session.native.exchange.side_effect = OSError("test")
            with self.assertRaises(TransportError): self.session.get(API_ENDPOINTS["reserve"])
        self.assertEqual(self.session.native.exchange.call_count, 2)


if __name__ == "__main__": unittest.main()
