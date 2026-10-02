# PAXwave

Music, podcasts and internet radio in one Android player.

PAXwave is **free software under the GNU General Public License v3.0**. See [LICENSE](LICENSE)
and [NOTICE](NOTICE). It is built on [BitChord](https://github.com/kushagrasinghx/BitChord) by
Kushagra Singh and contributors.

## Features
- **YouTube Music:** your library, playlists, mixes, lyrics, Automix, equaliser, downloads
  and Replay
- **Podcasts:** subscribe by RSS link, by search, or from Discover (charts by country and
  category). Includes an episode queue, AutoPlay of new episodes, resume, played state,
  downloads, per-show auto-download, a sleep timer, playback speed and 10/30-second skips.
- **Internet radio:** the Radio Browser directory with genres and countries, custom stream
  URLs, favourites, and live "now playing" titles
- One search across music, podcasts and radio
- Pinned favourites in the library

## Install
Download the latest APK from the [Releases](../../releases) page.

## Build
```
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleProdRelease
```
For a signed build, put `keystore.properties` and the keystore in the project root. Both are
git-ignored. See [keystore.properties.example](keystore.properties.example).

## Licence
PAXwave — Copyright (C) 2026 PAXwave contributors
Based on BitChord — Copyright (C) Kushagra Singh and BitChord contributors

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, version 3.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

Full credits for every component are in the app under **Settings → About PAXwave** and in
[NOTICE](NOTICE). PAXwave is not affiliated with Google, YouTube or Apple.
