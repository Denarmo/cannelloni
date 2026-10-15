<div align="center">

<img src=".github/resources/banner.svg" width="100%" alt="Cannelloni - A frontend with just the right amount of filling!">

<p align="center">
  <strong>The universal, distraction-free retro gaming frontend for Android.</strong><br>
  Built on the shoulders of <a href="https://github.com/CannoliHQ/cannoli">Cannoli</a> and inspired by <a href="https://github.com/shauninman/MinUI">MinUI</a>.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3%20%7C%20MIT-blue.svg?style=for-the-badge&labelColor=E8C896&color=555555" height="28" alt="License: GPLv3 | MIT"></a>
  <a href="https://github.com/Denarmo/cannelloni/releases"><img src="https://img.shields.io/badge/Release-v1.0.0-orange.svg?style=for-the-badge&labelColor=E8C896&color=555555" height="28" alt="Latest Release"></a>
  <a href="LEGAL.md"><img src="https://img.shields.io/badge/Legal-Notices-green.svg?style=for-the-badge&labelColor=E8C896&color=555555" height="28" alt="Legal Notices"></a>
  <a href="#supported-platforms--devices"><img src="https://img.shields.io/badge/Platform-Android%209%2B-brightgreen.svg?style=for-the-badge&labelColor=E8C896&color=555555" height="28" alt="Android 9+"></a>
  <a href="https://discord.com"><img src="https://img.shields.io/badge/Discord-@denarmo-5865F2.svg?style=for-the-badge&logo=discord&logoColor=white" height="28" alt="Discord @denarmo"></a>
</p>

</div>

---

## 🍝 What is Cannelloni?

**Cannelloni** is a modern, opinionated, and lightweight retro gaming launcher and player for Android.

Most modern frontends suffer from feature bloat: complex XML scrapers that fail on large collections, labyrinthine nested settings, laggy user interfaces, and endless tweaking required before you can even launch a game. 

Cannelloni takes the opposite path:
- **Instant launch:** Turn your device on and be playing within seconds.
- **Clean aesthetic:** Beautiful, distraction-free typography and iconography that puts your game library front and center.
- **Zero-fuss execution:** Drop your ROMs into organized folders and play immediately—no scraping, no metadata synchronization wait times, and no configuration headaches.
- **Consistent In-Game Experience:** A unified In-Game Menu (IGM) across all native cores for instant save states, disc switching, screen scaling, and PDF manual reading.

---

## 🚀 The Expanded Scope: From Handheld to Universal

Cannelloni began as a fork of [Cannoli](https://github.com/CannoliHQ/cannoli), an Android project inspired by Shaun Inman's legendary [MinUI](https://github.com/shauninman/MinUI). While the original project targeted dedicated 4:3 retro handhelds, **Cannelloni expands the horizon across the entire Android ecosystem**:

### 📱 Universal Device Support
- **Dedicated Handhelds:** Optimized for Retroid Pocket (Classic, 2S, 3+, 4/4 Pro, 5, Mini), AYN Odin & Odin 2, Anbernic RG-series, AYANEO Pocket, Logitech G Cloud, and Razer Edge.
- **Android TV & Google TV:** 10-foot Leanback interface support with full D-pad/remote navigation and couch-gaming multi-controller management.
- **Smartphones & Foldables:** Adaptive layouts that gracefully scale from compact portrait phones to widescreen landscape foldables and tablets.

### 🎮 Comprehensive Controls
- **Plug-and-Play Controller Auto-Mapping:** Automatic detection and mapping for Xbox, PlayStation DualShock/DualSense, Nintendo Switch Pro, 8BitDo, and generic HID gamepads.
- **Touch Screen Overlay Controls:** Responsive, low-profile virtual controls for touch-only phones and tablets.
- **Couch Multiplayer:** Multi-gamepad assignment for local multiplayer gaming on Android TV and docked handhelds.

### ⚡ Dual-Engine Hybrid Emulation
- **19 Native Libretro Cores:** Built-in, battle-tested cores compiled directly into Cannelloni for instant zero-configuration playback, seamless suspend/resume, and unified audio/video rendering.
- **Built-in Nintendo DS (melonDS) Core Integration:** Zero-configuration Nintendo DS emulation directly connected to Cannelloni's In-Game Menu (IGM). Features full touchscreen support, customizable shortcut button to swap screens on the fly, unified save states, and custom display shaders.
- **Built-in PSP (PPSSPP) Core Integration:** Zero-configuration PlayStation Portable emulation directly connected to Cannelloni's In-Game Menu (IGM). PPSSPP's system assets (fonts, UI atlas, language files) ship inside the APK and install themselves on first launch, with unified save states and emulation settings such as internal resolution available from the IGM.
- **Standalone Emulator Integration:** Deep-linking and activity launching for demanding systems (RetroArch, Dolphin, NetherSX2/AetherSX2, PPSSPP, DuckStation, Citra, Redream, etc.).

### 💾 Cloud Sync & Game Management
- **Cloud Save Syncing:** Support for WebDAV, RomM, and personal cloud storage to synchronize saves and save states seamlessly across devices.
- **Authentic PDF Game Manual Viewer:** Read original retro game instruction manuals directly in the In-Game Menu powered by Pdfium.
- **Multi-Disc Support:** Native `.m3u` playlist parsing and on-the-fly virtual disc swapping in the IGM.
- **RetroAchievements:** Track retro achievements and badges directly during gameplay.

### 🎨 Visuals & Custom Shaders
- **Tailored Shaders:** Custom `crt-cannelloni` shader alongside CRT-Aperture, CRT-Geom, CRT-Lottes, Zfast-CRT, and LCD grid filters.
- **Integer Scaling & Aspect Ratio Control:** Pixel-perfect integer scaling, authentic 4:3/3:2/8:7 aspect ratios, and custom stretching options.

---

## 📸 Screenshots

<p align="center">
  <img src=".github/resources/screenshots/main_menu.png" width="31%" alt="Main Menu">
  <img src=".github/resources/screenshots/favorites.png" width="31%" alt="Favorites">
  <img src=".github/resources/screenshots/igm.png" width="31%" alt="In-Game Menu">
</p>

---

## 🕹️ Supported Systems & Cores

Cannelloni includes built-in native Libretro cores for immediate playback:

| System | System Folder | Core / Engine | License |
| :--- | :--- | :--- | :--- |
| **Sony PlayStation** | `PS` | PCSX ReARMed / SwanStation | GPLv2 / GPLv3 |
| **Super Nintendo (SNES)** | `SFC` | Snes9x | Non-commercial |
| **Nintendo Entertainment System (NES)** | `FC` | Nestopia | GPLv2 |
| **Game Boy / Game Boy Color** | `GB` / `GBC` | Gambatte | GPLv2 |
| **Game Boy Advance** | `GBA` | mGBA | MPLv2.0 |
| **Sega Genesis / Mega Drive** | `MD` | Genesis Plus GX | Non-commercial |
| **Sega Master System / Game Gear** | `SMS` / `GG` | Genesis Plus GX | Non-commercial |
| **Arcade / Neo Geo** | `ARCADE` / `NEOGEO` | FBNeo / MAME 2003-Plus | Non-commercial / MAME |
| **PC Engine / TurboGrafx-16** | `PCE` | Mednafen PCE FAST | GPLv2 |
| **Atari 2600 / 7800** | `A2600` / `A7800` | Stella / ProSystem | GPLv2 |
| **Atari Lynx** | `LYNX` | Handy | Zlib |
| **Neo Geo Pocket / Color** | `NGP` / `NGPC` | Mednafen NGP | GPLv2 |
| **WonderSwan / Color** | `WS` / `WSC` | Mednafen WonderSwan | GPLv2 |
| **Virtual Boy** | `VB` | Mednafen VB | GPLv2 |
| **Nintendo DS (NDS)** | `NDS` | melonDS | GPLv3 |
| **Sony PSP** | `PSP` | PPSSPP | GPLv2+ |
| **Pokémon Mini** | `PKMN` | PokeMini | GPLv3 |

*Standalone emulator launchers are also available for Nintendo 64 (M64Plus FZ), Nintendo GameCube / Wii (Dolphin), and PlayStation 2 (NetherSX2). The PPSSPP standalone app remains available as an alternative launcher for PSP.*

---

## 📁 Directory Structure & Setup

Place your retro gaming collection on your internal storage or SD card under a folder named `Cannelloni` or `Roms`:

```text
/sdcard/Cannelloni/
├── Bios/
│   ├── scph5501.bin        (PlayStation BIOS)
│   ├── gba_bios.bin        (Game Boy Advance BIOS)
│   └── ...
├── Roms/
│   ├── FC/                 (NES / Famicom games)
│   ├── SFC/                (SNES / Super Famicom games)
│   ├── GBA/                (Game Boy Advance games)
│   ├── NDS/                (Nintendo DS .nds / .dsi games)
│   ├── PS/                 (PlayStation .chd / .cue / .m3u)
│   ├── PSP/                (PSP .iso / .cso / .chd / .pbp)
│   └── ...
├── Saves/                  (In-game battery saves)
└── States/                 (Instant save state snapshots)
```

1. Install the `Cannelloni.apk`.
2. Launch Cannelloni and grant storage access.
3. Select your ROMs directory when prompted.
4. Pick a game and play!

---

## ⚖️ Legal Information & Anti-Piracy Policy

Cannelloni takes intellectual property and copyright law seriously. Please review our comprehensive [**LEGAL.md**](LEGAL.md) document for complete terms.

### 1. Non-Affiliation and Trademarks
Cannelloni is an independent, community-driven open-source project and is **not affiliated with, authorized, endorsed, sponsored, or in any way associated with Nintendo, Sony, Sega, Microsoft, Atari, SNK, Bandai Namco, or any other entity**. All trademarks, system names, product names, logos, and brands are the property of their respective trademark holders and are used solely for descriptive, nominative identification under Fair Use principles.

### 2. Absolute Anti-Piracy Policy — No ROMs or BIOS Distributed
- **Cannelloni does NOT contain, distribute, host, share, or provide any copyrighted game ROMs, disk images, ISOs, BIOS files, firmware, decryption keys, or proprietary software.**
- Users are solely responsible for obtaining their own legitimate software and BIOS files dumped legally from hardware and physical media that they legally own.
- The project maintainers have a strict zero-tolerance policy against software piracy. Inquiries regarding where to find or download illegal ROMs or BIOS files will be summarily rejected and banned.

### 3. Emulation Legality
Cannelloni is purely an open-source graphical user interface (frontend), launcher, and media player. Emulation and clean-room reverse engineering for interoperability have been repeatedly recognized as lawful fair use under established United States federal court precedent (*Sony Computer Entertainment v. Connectix Corp.*, 203 F.3d 596; *Sega Enterprises Ltd. v. Accolade, Inc.*, 977 F.2d 1510).

### 4. DMCA Compliance
Cannelloni complies with the Digital Millennium Copyright Act (17 U.S.C. § 512). If you believe that content hosted directly within this repository infringes upon your copyright, please consult [**LEGAL.md**](LEGAL.md) for the formal notice and takedown procedure.

---

## 💖 Credits & Acknowledgments

Cannelloni is built upon the collective work of incredible developers and artists in the retro gaming and open-source communities:

- **[Cannoli](https://github.com/CannoliHQ/cannoli)**: The original Android MinUI frontend developed by the Cannoli team, providing the rock-solid foundation for this project.
- **[Shaun Inman](https://github.com/shauninman)**: Creator of [MinUI](https://github.com/shauninman/MinUI), whose vision of distraction-free, minimalist handheld gaming inspired this entire journey.
- **[Libretro / RetroArch](https://www.libretro.com)**: For the extraordinary cross-platform Libretro API and the vibrant emulator core ecosystem.
- **Emulation Core Teams**: The dedicated developers of melonDS (melonDS Team & Arisotura), PPSSPP (PPSSPP Team & Henrik Rydgård), PCSX ReARMed, SwanStation, Snes9x, Nestopia, Gambatte, mGBA, Genesis Plus GX, FBNeo, MAME 2003-Plus, Mednafen, Stella, ProSystem, Handy, and PokeMini.
- **Shader Authors**: EasyMode, cgwg, Themaister, Timothy Lottes, hunterk, Gigaherz, rsn8887, and SoltanGris42.
- **Font Designers**: M+ Fonts Project, BPreplay, and Nerd Fonts.
- **Libraries**: Apache Commons Compress, PdfiumAndroid, XZ for Java, and ZXing.

For an itemized list of licenses and attributions, see [credits.json](credits.json) and [LICENSE](LICENSE).

---

## 💬 Contact

- **Discord:** For questions, feedback, or general contact, reach out directly on Discord at **`@denarmo`**.

---

## 📄 License & Attribution

Cannelloni is open-source software with clear dual-attribution and permissive terms for downstream use:

- **Original Cannoli Codebase:** Copyright (C) 2026 Brandon T. Kowalski and Cannoli Contributors. Licensed under the [GNU General Public License v3.0 (GPL-3.0)](LICENSE).
- **Cannelloni Modifications & New Code:** Copyright (C) 2026 Denarmo and Cannelloni Contributors. Licensed under the [MIT License](LICENSE).

> [!NOTE]
> **Declaration of Non-Ownership & Open Permissions:**
> The maintainer of this repository (Denarmo) claims **no ownership** of the original fork's codebase. All original code belongs to the original authors.
> 
> All new code, modifications, and enhancements authored by Denarmo are released under the permissive **MIT License** and may be freely used, copied, modified, incorporated, and redistributed by anyone—including the upstream Cannoli project and third-party developers.
> 
> Because the complete, compiled Android application links and combines with upstream GPL-3.0 code, the distributed binary APK is conveyed under the [GNU General Public License v3.0](LICENSE).

See [**LICENSE**](LICENSE) for full legal text and [**LEGAL.md**](LEGAL.md) for full compliance information.