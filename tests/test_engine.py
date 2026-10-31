import json
from pathlib import Path
import sys
import unittest
from datetime import datetime
from types import SimpleNamespace
from unittest.mock import Mock, patch
from tempfile import TemporaryDirectory

sys.path.insert(0, str(Path(__file__).parents[1] / "app/src/main/python"))
import engine


class EngineTests(unittest.TestCase):
    def setUp(self):
        self.config = dict(username="test-user", password="not-a-real-password", departure="서울", arrival="부산",
            date="20991101", start="1630", end="1750", passengers=1, option="GENERAL_ONLY", confirmed=True)
        self.now = datetime(2099, 10, 31, 12, tzinfo=engine.KST)

    def test_confirmation_count_and_range_are_required(self):
        engine.validate(self.config, self.now)
        for change in ({"confirmed": False}, {"passengers": 0}, {"passengers": True}, {"end": "1600"}, {"arrival": "서울"}, {"password": ""}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                engine.validate({**self.config, **change}, self.now)

    def test_end_is_exclusive_and_seat_preference_is_enforced(self):
        start, end = engine.validate(self.config, self.now)
        train = SimpleNamespace(dep_date="20991101", dep_time="175000", has_general_seat=lambda: True)
        self.assertFalse(engine.matches(train, start, end, "GENERAL_ONLY"))
        train.dep_time = "163000"
        self.assertTrue(engine.matches(train, start, end, "GENERAL_ONLY"))
        train.has_general_seat = lambda: False
        self.assertFalse(engine.matches(train, start, end, "GENERAL_ONLY"))

    def test_sold_out_first_page_does_not_hide_later_available_trains(self):
        start, end = engine.validate(self.config, self.now)
        sold = SimpleNamespace(dep_date="20991101", dep_time="164000", has_general_seat=lambda: False)
        available = SimpleNamespace(dep_date="20991101", dep_time="170000", has_general_seat=lambda: True)
        client = Mock()
        client.trains.search.side_effect = [[sold], [available]]
        self.assertEqual(engine.search_window(client, self.config, start, end, [], lambda: False), [available])
        self.assertEqual(client.trains.search.call_count, 2)
        self.assertEqual(client.trains.search.call_args.kwargs["depart_after"].strftime("%H%M%S"), "164001")

    def test_stop_prevents_a_new_query(self):
        start, end = engine.validate(self.config, self.now)
        client = Mock()
        self.assertEqual(engine.search_window(client, self.config, start, end, [], lambda: True), [])
        client.trains.search.assert_not_called()

    def run_fake(self, reserve_error=None):
        client = Mock()
        train = SimpleNamespace(dep_date="20991101", dep_time="170000", has_general_seat=lambda: True)
        client.trains.search.return_value = [train]
        client.reservations.create.return_value = "TEST RESERVATION"
        if reserve_error: client.reservations.create.side_effect = reserve_error
        listener = Mock()
        listener.isStopped.return_value = False
        with patch("android_transport.install"), patch("pykorail.Korail", return_value=client), patch("engine.device_profile", return_value=None):
            engine.run(json.dumps(self.config), "unused-test-directory", listener)
        return client, listener

    def test_success_reserves_once_and_stops(self):
        client, listener = self.run_fake()
        client.reservations.create.assert_called_once()
        self.assertEqual(listener.onStatus.call_args.args[0], "success")
        client.close.assert_called_once()

    def test_uncertain_reservation_is_not_retried(self):
        client, listener = self.run_fake(OSError("simulated dropped connection"))
        client.reservations.create.assert_called_once()
        self.assertEqual(listener.onStatus.call_args.args[0], "uncertain")

    def test_access_restriction_stops_without_retry(self):
        from pykorail.exceptions import AccessRestrictedError
        client = Mock()
        client.trains.search.side_effect = AccessRestrictedError(403, "test restriction", "-8202")
        listener = Mock()
        listener.isStopped.return_value = False
        with patch("android_transport.install"), patch("pykorail.Korail", return_value=client), patch("engine.device_profile", return_value=None):
            engine.run(json.dumps(self.config), "unused-test-directory", listener)
        client.trains.search.assert_called_once()
        client.reservations.create.assert_not_called()
        self.assertEqual(listener.onStatus.call_args.args[0], "error")

    def test_device_identity_is_preserved(self):
        with TemporaryDirectory(dir=Path(__file__).parent) as directory:
            first = engine.device_profile(directory)
            second = engine.device_profile(directory)
            self.assertEqual(first.id, second.id)
            self.assertEqual(first.android_id, second.android_id)

    def test_transport_keeps_minimum_three_second_request_gap(self):
        import android_transport as transport
        session = object.__new__(transport.AndroidSession)
        session.headers = {"User-Agent": "test"}
        session.native = Mock()
        session.native.exchange.return_value = json.dumps({"status_code": 200, "text": "{}"})
        with patch.object(transport, "_last_finished", 100), patch.object(transport.time, "monotonic", side_effect=[101, 103]), patch.object(transport.time, "sleep") as sleep:
            response = session.get("https://example.invalid/search", params={"station": "서울"})
            sleep.assert_called_once_with(2.0)
            self.assertEqual(response.status_code, 200)
            self.assertIn("station=%EC%84%9C%EC%9A%B8", session.native.exchange.call_args.args[0])

    def test_native_network_failure_is_translated_without_request_details(self):
        import android_transport as transport
        from pykorail.exceptions import TransportError
        session = object.__new__(transport.AndroidSession)
        session.headers = {}
        session.native = Mock()
        session.native.exchange.side_effect = OSError("example private request data")
        with patch.object(transport.time, "sleep"), self.assertRaises(TransportError) as result:
            session.get("https://example.invalid")
        self.assertNotIn("private", str(result.exception))


if __name__ == "__main__": unittest.main()
