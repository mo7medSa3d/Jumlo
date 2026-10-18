"""Bound the urllib transport to web URLs. Does not change provider authentication or defeat DRM."""
import ipaddress
import socket
import urllib.parse


def validate_url(url, allow_http=False):
    if not isinstance(url, str) or len(url) > 65536 or any(ord(c) < 33 or ord(c) == 127 for c in url):
        raise ValueError('MEDIA_INVALID_NETWORK_URL')
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError('MEDIA_INVALID_NETWORK_URL')
    if parsed.scheme == 'http' and not allow_http:
        raise ValueError('MEDIA_HTTP_CONSENT_REQUIRED')
    try:
        port = parsed.port
    except ValueError as error:
        raise ValueError('MEDIA_INVALID_NETWORK_URL') from error
    if port is not None and not 1 <= port <= 65535:
        raise ValueError('MEDIA_INVALID_NETWORK_URL')
    hostname = parsed.hostname.lower().rstrip('.')
    if any(c in hostname for c in ('\\', '%', '/', '\x00')):
        raise ValueError('MEDIA_INVALID_NETWORK_URL')
    if hostname == 'localhost' or hostname.endswith(('.localhost', '.local', '.internal')):
        raise ValueError('MEDIA_PRIVATE_NETWORK_URL')
    try:
        address = ipaddress.ip_address(hostname)
    except ValueError:
        # inet_aton recognizes alternate IPv4 spellings such as 127.1 and 0x7f000001.
        # This is a local parser; it never resolves names or uses network access.
        try:
            address = ipaddress.IPv4Address(socket.inet_aton(hostname))
        except (OSError, ValueError):
            address = None
    if address is not None and not address.is_global:
        raise ValueError('MEDIA_PRIVATE_NETWORK_URL')
    return url


def install(allow_http=False):
    # Best-effort supplemental protection for yt-dlp's urllib handler. This is not a
    # complete network sandbox; other extractors and transports require device review.
    from yt_dlp.networking import _urllib
    original_send = _urllib.UrllibRH._send
    original_redirect = _urllib.RedirectHandler.redirect_request

    def guarded_send(self, request):
        validate_url(request.url, allow_http)
        return original_send(self, request)

    def guarded_redirect(self, req, fp, code, msg, headers, newurl):
        validate_url(newurl, allow_http)
        previous = urllib.parse.urlsplit(req.full_url)
        destination = urllib.parse.urlsplit(newurl)
        if previous.scheme == 'https' and destination.scheme == 'http':
            raise ValueError('MEDIA_TLS_DOWNGRADE')
        result = original_redirect(self, req, fp, code, msg, headers, newurl)
        if result and (previous.scheme, previous.hostname, previous.port) != (destination.scheme, destination.hostname, destination.port):
            for name in ('Authorization', 'Cookie', 'Proxy-Authorization'):
                result.remove_header(name)
                result.headers.pop(name, None)
                result.unredirected_hdrs.pop(name, None)
        return result

    _urllib.UrllibRH._send = guarded_send
    _urllib.RedirectHandler.redirect_request = guarded_redirect
