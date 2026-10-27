import base64

_KEY_CACHE = [None]


def _load_public_key():
    try:
        if _KEY_CACHE[0] is not None:
            return _KEY_CACHE[0]
        from elyx import assets
        asset = assets.get("public_key.pem")
        if asset is None:
            return None
        pem = asset.content_string()
        der = base64.b64decode(
            "".join(line for line in pem.splitlines() if not line.startswith("-----"))
        )
        from java.security import KeyFactory
        from java.security.spec import X509EncodedKeySpec
        pub = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        _KEY_CACHE[0] = pub
        return pub
    except Exception:
        return None


def build_sign_message(plugin_id, version, file_hash):
    return f"{plugin_id}:{version}:{file_hash}"


def verify_plugin_signature(plugin_id, version, file_hash, signature_b64):
    try:
        if not signature_b64:
            return False
        pub = _load_public_key()
        if pub is None:
            return False
        from java.security import Signature
        verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(pub)
        message = build_sign_message(plugin_id, version, file_hash).encode("utf-8")
        verifier.update(message)
        return verifier.verify(base64.b64decode(signature_b64))
    except Exception:
        return False
