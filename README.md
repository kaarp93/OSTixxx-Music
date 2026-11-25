# OSTixxx Music

> **Note on AI usage.** This project was developed largely with AI assistance and verified by Kaarp93. You can do whatever you want with it, including rewriting it without AI. If anyone has issues with AI usage in this project, let it be known.

**OSTixxx Music** is a video game music player that synthesizes each track live from the ROMs you import yourself. The app does not include any pre-recorded songs: it reads the score and instruments from the ROM (or runs its original sound code) and plays them back just as the console would.

## Download
Each release publishes a signed APK, `ostixxx-music-<version>.apk`. A new APK installs over the previous one. Requirements: Android 8.0 (API 26) or higher; portrait screen.

## Content
This project **does not contain any ROMs, save files, or files from Nintendo or other companies.** The app reads the ROMs you import onto your device; you must be their legitimate owner.

## Consoles and formats
| Platform | What the app does |
|---|---|
| **GBA** | m4a/MP2K engine: instruments, built-in Game Boy banks, envelopes, reverb, and effects. "100% original sound" option for the console's mixer. |
| **Nintendo DS** | SDAT: SSEQ, SBNK, SWAR, and STRM *streams*; games with music split across multiple SDATs. |
| **Game Boy / Game Boy Color** | Emulated LR35902 CPU and 4-channel APU; executes the game's original sound code. Dedicated engines for Pokémon Red/Blue/Yellow, Crystal, and Pokémon TCG 1 and 2. |
| **NES** | 6502 CPU and 2A03 APU, plus VRC6; mappers 0, 1, 2, 4, and 7; `.nsf` files. |
| **SNES** | SPC700 and DSP; `.spc` files and ROMs with drivers. |
| **PlayStation** (experimental) | `.cue`/`.bin` or `.iso` images: XA music and CD audio tracks, read directly from where they are. |
| **Other files** | `.gbs`, `.nsf`, `.vgm`/`.vgz`, and `.spc`. |

Each game is recognized by its CRC32 and ROM size. The app includes 110 automatic drivers and 363 experimental ones (GB/GBC, NES, and SNES); drivers can be imported and exported. Games that haven't been listened to yet are marked as experimental (✓/✗ per album).

## Features
- **Library** by console, in alphabetical order, with cover art; playlists and smart playlists (franchise, company, genre, console, decade, most played).
- **Player:** shuffle, loops, mute or solo channels, mini player, notification, and media controls.
- **Concert mode** in full screen, with 17 visualizations.
- **Comparison mode** for songs.
- **Effects:** 10-band EQ, reverb, chorus, mono/width/balance, and limiter, remembered per console.
- **Tags and catalog:** each track can be Song, SFX, Jingle, or Discard, with custom name and order.
- **Backup** `.ostx` of the entire library.
- **Optional libretro data** (cover art and metadata), only if you enable it.
- **Languages:** Catalan, English, and Spanish.

## Status
Roughly half of the games on the supported platforms work, with varying degrees of accuracy. If you want to request games, go ahead.

**Testing:** the app has been tested on a phone running Android 16, including with Android Auto.
Tested on a second device with Android 9 and a third one with Android 13 with success, although Android 9 struggles a bit with some synths.

**What hasn't been verified:**
- Experimental games and new drivers haven't all been listened to.
- Other devices and other Android versions.

## Known issues

Sound doesn't stop when another app starts or when calling.
PS1 doesn't have visuals support.
Slight battery overconsumption might happen in some devices.
HyperOS devices sometimes kill the whole app, battery saver settings per app should be on no optimization on such devices.

## Open source and credits
**MIT** license (`LICENSE` file). The sound engines were written based on open disassembly and reimplementation projects, each with its own license. Optional data (cover art and genres) comes from libretro (CC BY-SA 4.0). The app font is Work Sans (SIL Open Font License 1.1, `OFL-WorkSans.txt` file). Concert mode graphics (`concert/herois.png`, `concert/enemics.png`) are from ImmoHub, by Kaarp93, GPL license.

## Contributing
Feedback, bugs, and ROMs that don't play are welcome.
