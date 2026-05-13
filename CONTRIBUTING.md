# Contributing to Melodix

Thanks for taking the time to contribute. Here's everything you need to get started.

---

## Table of Contents

- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Features](#suggesting-features)
- [Pull Request Process](#pull-request-process)
- [Code Style](#code-style)

## Getting Started

1. Fork the repository
2. Clone your fork:

   ```bash
   git clone https://github.com/your-username/melodix.git
   cd melodix
   ```

3. Create a new branch for your work:

   ```bash
   git checkout -b feature/your-feature-name
   ```

4. Make your changes, then push and open a Pull Request against `main`.

---

## How to Contribute

- **Bug fixes** — always welcome, no prior discussion needed for small fixes.
- **New features** — open an issue first to align on scope before writing code.
- **Docs / README improvements** — open a PR directly.
- **yt-dlp engine integration improvements** — see `engine/` module.

---

## Reporting Bugs

Open an issue using the **Bug Report** template and include:

- Device model and Android version
- Melodix version (Settings → About)
- Steps to reproduce
- Expected vs actual behavior
- Logs if available (Settings → Export Logs)

---

## Suggesting Features

Open an issue using the **Feature Request** template. Describe:

- The problem you're solving
- Your proposed solution
- Any alternatives you've considered

---

## Pull Request Process

1. Ensure your branch is up to date with `main` before opening a PR.
2. PRs must pass all CI checks before merging.
3. Keep PRs focused — one feature or fix per PR.
4. Add or update tests where applicable.
5. Update `CHANGELOG.md` under `[Unreleased]` with a summary of your change.
6. A maintainer will review and merge or request changes within a reasonable time.

---

## Code Style

- Follow the existing code conventions in the file you're editing.
- No commented-out dead code in PRs.
- Meaningful commit messages: `fix: handle null playlist on Duo join` not `fix stuff`.

---

By contributing, you agree your contributions will be licensed under the project's [MIT License](LICENSE).
