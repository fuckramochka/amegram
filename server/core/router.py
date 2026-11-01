import re
import json
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable, Dict, List, Tuple, Any
from server.core.response import Response, json_response, error_response

class Request:
    def __init__(self, method: str, path: str, query: Dict[str, List[str]], headers: Dict[str, str], body_bytes: bytes, params: Dict[str, str]):
        self.method = method.upper()
        self.path = path
        self.query = {k: v[0] if len(v) == 1 else v for k, v in query.items()}
        self.headers = headers
        self.body_bytes = body_bytes
        self.params = params
        self._json = None

    def json(self) -> Any:
        if self._json is None:
            if not self.body_bytes:
                return {}
            try:
                self._json = json.loads(self.body_bytes.decode("utf-8"))
            except Exception:
                self._json = {}
        return self._json

class Router:
    def __init__(self):
        # List of (method, regex_pattern, param_names, handler)
        self.routes: List[Tuple[str, re.Pattern, List[str], Callable]] = []

    def add(self, method: str, path_pattern: str, handler: Callable):
        method = method.upper()
        # Convert path pattern like "/api/badges/{user_id}" to regex
        param_names = []
        def replace_param(match):
            name = match.group(1)
            param_names.append(name)
            return r"([^/]+)"

        regex_str = "^" + re.sub(r"\{([a-zA-Z0-9_]+)\}", replace_param, path_pattern) + "$"
        regex = re.compile(regex_str)
        self.routes.append((method, regex, param_names, handler))

    def get(self, path: str):
        def decorator(handler: Callable):
            self.add("GET", path, handler)
            return handler
        return decorator

    def post(self, path: str):
        def decorator(handler: Callable):
            self.add("POST", path, handler)
            return handler
        return decorator

    def delete(self, path: str):
        def decorator(handler: Callable):
            self.add("DELETE", path, handler)
            return handler
        return decorator

    def dispatch(self, method: str, raw_path: str, headers: Dict[str, str], body: bytes) -> Response:
        parsed = urllib.parse.urlparse(raw_path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        # Preflight CORS
        if method == "OPTIONS":
            return Response(b"", status=204)

        for route_method, regex, param_names, handler in self.routes:
            if route_method == method or route_method == "*":
                match = regex.match(path)
                if match:
                    params = dict(zip(param_names, match.groups()))
                    req = Request(method, path, query, headers, body, params)
                    try:
                        res = handler(req)
                        if isinstance(res, Response):
                            return res
                        return json_response(res)
                    except Exception as e:
                        import traceback
                        traceback.print_exc()
                        return error_response(f"Internal server error: {str(e)}", status=500, code="SERVER_ERROR")

        return error_response(f"Route not found: {method} {path}", status=404, code="NOT_FOUND")

class YumiHTTPHandler(BaseHTTPRequestHandler):
    router: Router = None

    def log_message(self, format, *args):
        # Clean formatted logging: "[METHOD] /path -> code"
        print(f"[{self.command}] {self.path} - {args[1]}")

    def do_request(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length) if content_length > 0 else b""
        
        headers_dict = {k.lower(): v for k, v in self.headers.items()}
        res = self.router.dispatch(self.command, self.path, headers_dict, body)
        
        self.send_response(res.status)
        for k, v in res.headers.items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(res.body_bytes)

    def do_GET(self): self.do_request()
    def do_POST(self): self.do_request()
    def do_PUT(self): self.do_request()
    def do_DELETE(self): self.do_request()
    def do_OPTIONS(self): self.do_request()
