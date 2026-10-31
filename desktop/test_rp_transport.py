"""Network-independent regression tests for Shadow RP model requests."""
import io
import json
import unittest
from email.message import Message
from urllib.error import HTTPError, URLError

from rp_transport import chat_completion


class Response:
    def __init__(self, body):
        self.body = io.BytesIO(body)
    def __enter__(self):
        return self
    def __exit__(self, *args):
        self.body.close()
    def read(self, size=-1):
        return self.body.read(size)


class Client:
    def __init__(self, *results):
        self.results = list(results)
        self.requests = []
    def open(self, request, timeout):
        self.requests.append((request, timeout))
        result = self.results.pop(0)
        if isinstance(result, Exception):
            raise result
        return Response(result)


def ok(content="Ответ"):
    return json.dumps({"choices": [{"message": {"content": content}}]}).encode()


def http_error(code, retry_after=None):
    headers = Message()
    if retry_after is not None:
        headers["Retry-After"] = retry_after
    return HTTPError("https://example.test/v1/chat/completions", code, "error",
                     headers, io.BytesIO(b"secret server response"))


class TransportTests(unittest.TestCase):
    def test_success_keeps_message_and_authorization(self):
        client = Client(ok("Привет"))
        result = chat_completion("https://example.test/v1", "private-key", "test",
                                 [{"role": "user", "content": "hi"}],
                                 opener=client, sleep=lambda _: None)
        self.assertEqual(result, "Привет")
        request, timeout = client.requests[0]
        self.assertEqual(request.full_url, "https://example.test/v1/chat/completions")
        self.assertEqual(request.get_header("Authorization"), "Bearer private-key")
        self.assertEqual(timeout, 45)

    def test_rate_limit_then_success_and_retry_after_cap(self):
        delays = []
        client = Client(http_error(429, "900"), ok())
        self.assertEqual(chat_completion("https://example.test/v1", "", "model", [],
                                         opener=client, sleep=delays.append), "Ответ")
        self.assertEqual(delays, [3.0])
        self.assertEqual(len(client.requests), 2)

    def test_rejects_unauthorized_without_retry_or_leaking_body(self):
        client = Client(http_error(401))
        with self.assertRaisesRegex(ValueError, "HTTP 401") as ctx:
            chat_completion("https://example.test/v1", "secret", "model", [],
                            opener=client, sleep=lambda _: None)
        self.assertNotIn("secret server", str(ctx.exception))
        self.assertEqual(len(client.requests), 1)

    def test_connection_retry_exhaustion_is_user_readable(self):
        client = Client(URLError("down"), URLError("down"), URLError("down"))
        with self.assertRaisesRegex(ValueError, "Нет соединения"):
            chat_completion("https://example.test/v1", "", "model", [],
                            opener=client, sleep=lambda _: None)
        self.assertEqual(len(client.requests), 3)

    def test_malformed_or_oversized_response_never_retried(self):
        for body in (b"{}", b"not-json", b"x" * (2 * 1024 * 1024 + 1)):
            client = Client(body)
            with self.subTest(length=len(body)), self.assertRaises(ValueError):
                chat_completion("https://example.test/v1", "", "model", [],
                                opener=client, sleep=lambda _: None)
            self.assertEqual(len(client.requests), 1)

    def test_response_length_is_bounded(self):
        client = Client(ok("a" * 25000))
        result = chat_completion("https://example.test/v1", "", "model", [],
                                 opener=client, sleep=lambda _: None)
        self.assertEqual(len(result), 20000)


if __name__ == "__main__":
    unittest.main()
