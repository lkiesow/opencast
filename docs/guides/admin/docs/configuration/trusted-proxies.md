Trusted Proxies
===============

Opencast is usually run behind a reverse proxy which terminates TLS and forwards requests to Opencast itself. Since
Opencast only ever sees a connection coming from that proxy, the proxy has to tell it about the original request using
these headers:

- `X-Forwarded-For` – the address of the client the proxy received the request from
- `X-Forwarded-Proto` / `X-Forwarded-SSL` – whether the client was talking HTTPS

Nothing stops a regular client from sending those headers as well. Opencast therefore only honors them if the request
was received from a host you declared to be a reverse proxy of yours. Requests from anywhere else are taken at face
value and the headers are ignored.

Configuration
-------------

The list of proxies lives in `etc/org.opencastproject.kernel.filter.proxy.TrustedProxies.cfg`:

```properties
trusted.proxies=127.0.0.0/8, ::1, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, fc00::/7
```

Entries are IP addresses or CIDR ranges and may be IPv4 or IPv6. The value shown above is the default and covers the
loopback interface plus the private address ranges, which is where the reverse proxy sits in a typical installation.

Narrow this down to the address your proxy actually uses if you can. Any host matching this list gets to choose which
client address Opencast sees, so the shorter the list, the better. Adding public address space effectively turns the
protection off.

Setting the option to an empty value makes Opencast ignore forwarding headers on every request.

Which requests this affects
---------------------------

Whether Opencast believes the forwarding headers of a request affects:

- **Signed URLs restricted to a client** –
  [URL signing](stream-security/stream-security-config.md) with `url.signing.use.client.ip` binds signed URLs for the
  video player, the admin interface and the video editor to the client address. If forwarding headers were trusted
  unconditionally, a client could pick the address such a URL is bound to, which is why that feature is only
  meaningful together with a correct list of proxies here.
- **User tracking** – sessions recorded by the user tracking service store the client address.
- **Logging** – the client address shows up in log messages about a request.
- **HTTPS detection** – `X-Forwarded-Proto` and `X-Forwarded-SSL` tell Opencast that the client used HTTPS, which
  determines the scheme of URLs Opencast generates. If your proxy is not trusted, those URLs fall back to plain HTTP.

Making sure your proxy overwrites the header
--------------------------------------------

A trusted proxy is trusted for one thing only: the address it appends itself. Opencast reads the
`X-Forwarded-For` list from the right and skips over addresses belonging to configured proxies, so entries a client
sent along are discarded on the way. This works regardless of whether your proxy replaces or extends the header.

It is still cleaner to have the proxy overwrite whatever the client sent. See the
[Nginx](https/nginx.md) and [Apache httpd](https/apache-httpd.md) guides for configuration examples.
