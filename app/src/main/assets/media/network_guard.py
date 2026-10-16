"""HTTP(S)-only extraction; never relax TLS or carry credentials across origins.

The adapter intentionally uses yt-dlp's urllib transport. Alternate impersonation
transports are not enabled until their redirect behavior is separately qualified.
"""
import urllib.parse
import urllib.request


def validate_url(url, allow_http=False):
    if not isinstance(url, str) or len(url) > 65536 or any(ord(c) < 33 or ord(c) == 127 for c in url):
        raise ValueError('MEDIA_INVALID_NETWORK_URL')
    value = urllib.parse.urlsplit(url)
    if value.scheme not in ('http', 'https') or not value.hostname or value.username is not None or value.password is not None:
        raise ValueError('MEDIA_INVALID_NETWORK_URL')
    if value.port is not None and not 1 <= value.port <= 65535:
        raise ValueError('MEDIA_INVALID_NETWORK_URL')
    if value.scheme == 'http' and not allow_http:
        raise ValueError('MEDIA_HTTP_CONSENT_REQUIRED')
    return url


def origin(url):
    p = urllib.parse.urlsplit(url)
    return p.scheme, p.hostname.lower(), p.port or (443 if p.scheme == 'https' else 80)


def redirect(request, url, allow_http=False):
    validate_url(url, allow_http)
    old = request.full_url
    if urllib.parse.urlsplit(old).scheme == 'https' and urllib.parse.urlsplit(url).scheme == 'http':
        raise ValueError('MEDIA_TLS_DOWNGRADE')
    cross_origin = origin(old) != origin(url)
    # Only forwarding harmless negotiated headers is safe when an extractor
    # invents a custom token header whose name this application cannot know.
    public = {'accept', 'accept-language', 'accept-encoding', 'user-agent', 'range', 'if-range'}
    headers = {k: v for k, v in request.header_items()
               if k.lower() != 'cookie' and (not cross_origin or k.lower() in public)}
    return urllib.request.Request(url, headers=headers, data=request.data, method=request.get_method())


def install(allow_http=False):
    from yt_dlp.networking import common, _urllib
    original_send = _urllib.UrllibRH._send
    original_redirect = _urllib.RedirectHandler.redirect_request

    def send(self, request):
        validate_url(request.url, allow_http)
        return original_send(self, request)

    def follow(self, req, fp, code, msg, headers, newurl):
        sanitized = redirect(req, newurl, allow_http)
        # Retain upstream POST/GET and status semantics; replace only headers.
        target = original_redirect(self, req, fp, code, msg, headers, newurl)
        if target is not None:
            target.headers = dict(sanitized.headers)
            target.unredirected_hdrs = {}
            if target.data is None:
                target.remove_header('Content-length')
                target.remove_header('Content-type')
        return target

    _urllib.UrllibRH._send = send
    _urllib.RedirectHandler.redirect_request = follow
    common._REQUEST_HANDLERS.clear()
    common._REQUEST_HANDLERS[_urllib.UrllibRH.RH_KEY] = _urllib.UrllibRH
