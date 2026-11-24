# OSTixxx Music

> **Nota sobre l'ús d'IA.** Aquest projecte s'ha desenvolupat majoritàriament amb ajuda d'IA i amb la comprovació de Kaarp93. Podeu fer-ne el que vulgueu, també refer-lo sense IA. Si algú té problemes amb l'ús d'IA en aquest projecte, que ho faci saber.

**OSTixxx Music** és un reproductor de música de videojocs que sintetitza cada tema en directe a partir de les ROM que tu mateix importes. L'app no inclou cap cançó gravada: llegeix la partitura i els instruments de la ROM (o n'executa el codi de so original) i els reprodueix com ho faria la consola.

## Descàrrega
Cada release publica una APK signada, `ostixxx-music-<versió>.apk`. Una APK nova s'instal·la per sobre de l'anterior. Requisits: Android 8.0 (API 26) o superior; pantalla vertical.

## Contingut
Aquest projecte **no conté cap ROM, cap partida desada ni cap fitxer de Nintendo ni d'altres empreses.** L'app llegeix les ROM que importes al teu dispositiu; cal ser-ne propietari legítim.

## Consoles i formats
| Plataforma | Què fa l'app |
|---|---|
| **GBA** | Motor m4a/MP2K: instruments, bancs de Game Boy integrats, envolupants, reverb i efectes. Opció «so original 100 %» per al mesclador de la consola. |
| **Nintendo DS** | SDAT: SSEQ, SBNK, SWAR i *streams* STRM; jocs amb la música repartida en diversos SDAT. |
| **Game Boy / Game Boy Color** | CPU LR35902 i APU de 4 canals emulades; s'executa el codi de so original del joc. Motors propis de Pokémon Vermell/Blau/Groc, Cristall i Pokémon TCG 1 i 2. |
| **NES** | CPU 6502 i APU 2A03, més VRC6; mappers 0, 1, 2, 4 i 7; fitxers `.nsf`. |
| **SNES** | SPC700 i DSP; fitxers `.spc` i ROM amb drivers. |
| **PlayStation** (experimental) | Imatges `.cue`/`.bin` o `.iso`: música XA i pistes de CD àudio, llegides d'on són. |
| **Altres fitxers** | `.gbs`, `.nsf`, `.vgm`/`.vgz` i `.spc`. |

Cada joc es reconeix pel CRC32 i la mida de la ROM. L'app inclou 110 drivers automàtics i 363 d'experimentals (GB/GBC, NES i SNES); els drivers es poden importar i exportar. Els jocs que encara no s'han escoltat es marquen com a experimentals (✓/✗ per àlbum).

## Funcions
- **Biblioteca** per consola, en ordre alfabètic, amb portades; llistes de reproducció i llistes automàtiques (saga, empresa, gènere, consola, dècada, més reproduïdes).
- **Reproductor:** aleatori, bucles, canals silenciables o en solo, mini reproductor, notificació i controls multimèdia.
- **Mode concert** a pantalla completa, amb 17 visualitzacions.
- **Mode comparació** de cançons.
- **Efectes:** EQ de 10 bandes, reverb, chorus, mono/amplada/balanç i limitador, recordats per consola.
- **Etiquetes i catàleg:** cada pista pot ser Cançó, Sfx, Jingle o Descarta, amb nom i ordre propis.
- **Còpia de seguretat** `.ostx` de tota la llibreria.
- **Dades opcionals de libretro** (portades i metadades), només si les actives.
- **Idiomes:** català, anglès i castellà.

## Estat
Aproximadament la meitat dels jocs de les plataformes suportades funcionen, amb més o menys encert. Si voleu demanar jocs, endavant.

**Proves:** l'app s'ha provat en un mòbil amb Android 16, també amb Android Auto.

**Què no s'ha pogut comprovar:**
- Els jocs experimentals i els drivers nous no s'han escoltat tots.
- Altres dispositius i altres versions d'Android.

## Codi obert i crèdits
Llicència **MIT** (fitxer `LICENSE`). Els motors de so s'han escrit a partir de projectes oberts de desassemblatge i reimplementació, cadascun amb la seva llicència. Les dades opcionals (portades i gèneres) vénen de libretro (CC BY-SA 4.0). Els gràfics del mode concert (`concert/herois.png`, `concert/enemics.png`) són d'ImmoHub, de Kaarp93, llicència GPL.

## Contribuir
Els comentaris, els errors i les ROM que no sonen són benvinguts.
