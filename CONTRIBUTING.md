# Contributing to Cannelloni

Thank you for considering contributing to Cannelloni!

Cannelloni is an open-source project dedicated to delivering a clean, distraction-free retro gaming experience across Android devices. We welcome contributions that align with the project's vision of simplicity, performance, and reliability.

---

## 📜 Legal & Anti-Piracy Compliance

Before contributing code, documentation, or issues, please review our [**LEGAL.md**](LEGAL.md).

- **Strict No-ROMs / No-BIOS Policy:** Never submit pull requests, issues, or commit messages containing copyrighted game ROMs, BIOS images, proprietary keys, or links to pirated content. Any such submissions will be closed and removed immediately.
- **Clean Code & Attribution:** Ensure any third-party code, shaders, or assets included have compatible open-source licenses (GPLv3, Apache 2.0, MIT, Zlib, etc.) and are properly attributed in [`credits.json`](credits.json).

---

## 🛠️ How to Contribute

### 1. Translations & Localization
Community translations are managed through Crowdin and localized XML resource files. See [**TRANSLATING.md**](TRANSLATING.md) for full instructions on how to contribute translations for your language without writing any code.

### 2. Issues & Feature Discussions
If you encounter a bug or have an idea that aligns with Cannelloni's minimal-fuss design philosophy:
- Search existing issues to ensure it hasn't already been reported.
- Open a detailed issue describing your Android device model, Android OS version, controller type, and steps to reproduce.

### 3. Pull Requests
Cannelloni maintains a focused, opinionated feature set to avoid frontend bloat. If you plan to work on a major architectural change or new feature, please open an issue first to discuss the design.

To submit a pull request:
1. Fork the repository and create your feature branch: `git checkout -b feature/my-new-feature`.
2. Ensure your changes compile cleanly and adhere to existing Kotlin / C++ code formatting.
3. If you update credits or third-party components, run `python scripts/sync_credits.py`.
4. Open a Pull Request with a clear explanation of what changed and why.

---

## 💖 Upstream Community
Cannelloni builds on the shoulders of giants in the emulation community, particularly [Cannoli](https://github.com/CannoliHQ/cannoli) and [MinUI](https://github.com/shauninman/MinUI). Please support their upstream projects as well!

---

## 💬 Contact

- **Discord:** You can reach out directly on Discord: [`@denarmo`](https://discord.com).