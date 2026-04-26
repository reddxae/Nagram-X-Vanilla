# Vanilla
Yet another Nagram fork with Telegram official branding.

Based on Telegram 12.1.1 without shitty liquid ass redesign. Intended for personal use with various QoL fixes.

This project is AI-assisted.

## Why?
The point is... ugh, well, I'm more into ReVanced-style mods than making fully rebranded ones. I love that aesthetic when the app looks essentially the same as the original, just with some useful additions — rather than going in a completely different design direction. Besides, I don't have time for that stuff.

Vanilla utilizes GitHub Actions workers to produce builds and push them to users. This ensures that builds are transparent, meaning they contain exactly what's available in the repository's source code.

An important paradigm of this client is No Cringe. You won't find any epic ripple animations here when you toggle settings, neither that half-broken "chat centering" which isn't properly centered and turns into an unwanted overflown scrollbar, nor even worse stuff — built-in AI assistants. There are also no client-side user blacklists, no "Extras" in this source code or paywalled features — and there won't ever be.

## Changes over [regular Nagram X](https://github.com/risin42/NagramX)
- Telegram official branding over the app (logo, name, icons, splash)
- Enhanced Monet palette generation (more vibrant and contrast)
- Configurable resolution and bitrate for Video Messages
- Backported TLS Client Hello stack from upstream (fixes MTProto for Russian users)
- Hiding stickers/emoji/GIF panel on scroll with "Hide keyboard on Scroll" enabled
- Keep Send as channel available when its hidden; just hold emoji button
- Separate "Disable Swipe to Next..." for Channels and Forums
- Use Camera2 API for Video Messages with enhanced implementation
  - Seamless Switching option
  - Stabilization option
  - Option to choose default rear camera from modules available
- Option to start Video Messages recording with Rear Camera by default or ask before recording
- Option to save the zoom position until you change it manually in Video Messages
- Option to disable "Gooey" avatar animation
- Replicate iOS-like camera switching style with blurred fade-in effect in Video Messages 
- "Disable Avatar Blur" feature will UI with pre-12.0.0 profile behaviour (kind of)
- Configurable translucency of the panels
- Relative online time (e.g., "last seen 5 minutes ago" instead of "last seen at 15:10")
- Hide Direct Share button everywhere with "Hide Share Button Next to Post"
- Icons for Attachments Tabs
- Replace subscribers/members caption to monochrome icon
- Thousands separators for large numbers, also for sub counter when "Disable Number Rounding" enabled
- Hide Stars rating and Gift button from profile
- Hide last seen Premium prompt ("when?" badge next to last seen)
- Hide the Gifts tab from user/channel profiles completely / move it to the last tab
- Backported table parsing support from upstream
- Separate "Don't Send Typing" for chats and groups
- Changed Nagram's set of by-default enabled features in the sake of being predictable
- Overall UI improvements
  - Fixed abnormal CPU usage caused by PiP source flapping and notification spam (Telegram bug)
  - Fixed centering of online status relatively to user name (Telegram bug)
  - Fixed views counter overlapping number of voters if the poll message forwarded (Telegram bug)
  - Fixed dividers inconsistency over the app
  - Omit seconds from business hours in profiles
  - Do not convert date in media viewer to numeric format
  - Other tweaks to beautify appearance

## How to build yourself

### Locally

1. Obtain API credentials from [Telegram Developer Portal](https://my.telegram.org/auth). Create `local.properties` in the project root with:

   ```properties
   TELEGRAM_API_ID=<your_telegram_api_id>
   TELEGRAM_API_HASH=<your_telegram_api_hash>
   ```

2. Place `release.keystore` with your keystore in TMessagesProj and add signing configuration to `local.properties`:

   ```properties
   KEYSTORE_PASS=<your_keystore_password>
   ALIAS_NAME=<your_alias_name>
   ALIAS_PASS=<your_alias_password>
   ```

3. Place `TMessagesProj/google-services.json` with your own configuration file to obtain FCM support.

4. Open the project in Android Studio to start building.

### On GitHub runner

1. Encode your google-services.json in base64:

   ```bash
   base64 -w 0 google-services.json
   ```

   Create new [repository secret](https://github.com/reddxae/Nagram-X-Vanilla/settings/secrets/actions) named `GOOGLE_SERVICES_JSON` and fill it with your `base64` output.

2. Encode your keystore in base64:

   ```bash
   base64 -w 0 release.keystore
   ```

   Create new [repository secret](https://github.com/reddxae/Nagram-X-Vanilla/settings/secrets/actions) named `KEYSTORE_BASE64` and fill it with your `base64` output.


3. Create new [repository secret](https://github.com/reddxae/Nagram-X-Vanilla/settings/secrets/actions) named `LOCAL_PROPERTIES` and fill it with these contents:

   ```properties
   TELEGRAM_API_ID=<your_telegram_api_id>
   TELEGRAM_API_HASH=<your_telegram_api_hash>
   KEYSTORE_PASS=<your_keystore_password>
   ALIAS_NAME=<your_alias_name>
   ALIAS_PASS=<your_alias_password>
   ```

4. Trigger the Build APK workflow.

## Many thanks for those who contribute to open source

- [Nekogram](https://github.com/Nekogram/Nekogram) for being the bare minimum basis of all our forks
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram) for adding new features and being helpful
- [Dr4iv3rNope](https://github.com/Dr4iv3rNope/NotSoAndroidAyuGram) for reverse-engineering AyuGram's proprietary features
- [Nagram](https://github.com/NextAlone/Nagram) and [Nagram X](https://github.com/risin42/NagramX) for collecting awesome things
- [OctoGram](https://github.com/OctoGramApp/OctoGram) for its excellent design solutions
