import json
from typing import Any, Dict

CORS_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Requested-With, apikey",
}

class Response:
    def __init__(self, body: Any, status: int = 200, headers: Dict[str, str] = None, content_type: str = "application/json"):
        self.status = status
        self.content_type = content_type
        self.headers = dict(CORS_HEADERS)
        if headers:
            self.headers.update(headers)
        
        if content_type == "application/json":
            self.body_bytes = json.dumps(body, ensure_ascii=False, indent=2).encode("utf-8")
        elif isinstance(body, str):
            self.body_bytes = body.encode("utf-8")
        elif isinstance(body, bytes):
            self.body_bytes = body
        else:
            self.body_bytes = str(body).encode("utf-8")
            
        self.headers["Content-Type"] = f"{content_type}; charset=utf-8"
        self.headers["Content-Length"] = str(len(self.body_bytes))

def json_response(data: Any, status: int = 200, headers: Dict[str, str] = None) -> Response:
    return Response(data, status=status, headers=headers, content_type="application/json")

def error_response(message: str, status: int = 400, code: str = "BAD_REQUEST") -> Response:
    return Response({"error": message, "code": code, "status": status}, status=status, content_type="application/json")

def html_response(html: str, status: int = 200) -> Response:
    return Response(html, status=status, content_type="text/html")
