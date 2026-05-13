# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.x     | ✅ Yes    |
| < 1.0   | ❌ No     |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Report security issues privately by emailing: **<soumom764@gmail.com>**

Include:

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Your suggested fix (if any)

You will receive an acknowledgment within **72 hours**. If the issue is confirmed, a patch will be prioritized and you will be credited in the release notes (unless you prefer to remain anonymous).

---

## Scope

Issues in scope:

- WebRTC Duo connection security / session hijacking
- Downloader engine executing unintended code
- Unauthorized access to local media files
- Data leakage over Duo sync

Out of scope:

- Vulnerabilities in yt-dlp itself (report to [yt-dlp upstream](https://github.com/yt-dlp/yt-dlp/security))
- Issues on unsupported Android versions
- Social engineering attacks
