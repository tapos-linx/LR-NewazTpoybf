"""
Runtime compatibility layer for FastAPI and Pydantic.
Uses real `fastapi` and `pydantic` when installed in the Python environment,
and provides lightweight stdlib-compatible shims when running unit tests in minimal
containers where third-party web packages are not pre-installed.
"""

from __future__ import annotations

import copy
from typing import Any, Callable, Dict

try:
    from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile
    from fastapi.middleware.cors import CORSMiddleware
    from fastapi.responses import JSONResponse, StreamingResponse
    from pydantic import BaseModel, Field
    HAS_FASTAPI = True
except ImportError:
    HAS_FASTAPI = False

    class HTTPException(Exception):  # type: ignore
        def __init__(self, status_code: int, detail: str = ""):
            super().__init__(detail)
            self.status_code = status_code
            self.detail = detail

    class UploadFile:  # type: ignore
        def __init__(self, filename: str = "", content: bytes = b""):
            self.filename = filename
            self._content = content

        async def read(self) -> bytes:
            return self._content

    def File(default: Any = None, **kwargs: Any) -> Any:  # type: ignore
        return default

    def Form(default: Any = None, **kwargs: Any) -> Any:  # type: ignore
        return default

    class StreamingResponse:  # type: ignore
        def __init__(self, content: Any, media_type: str = "", headers: Dict[str, str] | None = None):
            self.content = content
            self.media_type = media_type
            self.headers = headers or {}

    class JSONResponse:  # type: ignore
        def __init__(self, status_code: int = 200, content: Any = None):
            self.status_code = status_code
            self.content = content

    class APIRouter:  # type: ignore
        def __init__(self, *args: Any, **kwargs: Any):
            self.routes = []

        def get(self, path: str, **kwargs: Any) -> Callable[[ Any], Any]:
            def decorator(fn: Any) -> Any:
                self.routes.append(("GET", path, fn))
                return fn
            return decorator

        def post(self, path: str, **kwargs: Any) -> Callable[[Any], Any]:
            def decorator(fn: Any) -> Any:
                self.routes.append(("POST", path, fn))
                return fn
            return decorator

    class _FieldSpec:
        def __init__(self, default: Any = None, default_factory: Callable[[], Any] | None = None):
            self.default = default
            self.default_factory = default_factory

    def Field(default: Any = None, default_factory: Callable[[], Any] | None = None, **kwargs: Any) -> Any:  # type: ignore
        return _FieldSpec(default=default, default_factory=default_factory)

    class BaseModel:  # type: ignore
        def __init__(self, **kwargs: Any):
            for cls in reversed(self.__class__.__mro__):
                for k, v in cls.__dict__.items():
                    if k.startswith("_") or callable(v):
                        continue
                    if isinstance(v, _FieldSpec):
                        val = v.default_factory() if v.default_factory is not None else copy.deepcopy(v.default)
                        setattr(self, k, val)
                    else:
                        setattr(self, k, copy.deepcopy(v))
            for k, v in kwargs.items():
                setattr(self, k, v)

        def model_dump(self) -> Dict[str, Any]:
            def _dump(val: Any) -> Any:
                if isinstance(val, BaseModel):
                    return val.model_dump()
                if isinstance(val, list):
                    return [_dump(x) for x in val]
                if isinstance(val, dict):
                    return {dk: _dump(dv) for dk, dv in val.items()}
                return val

            return {k: _dump(v) for k, v in self.__dict__.items() if not k.startswith("_")}
