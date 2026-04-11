# Nagram X Vanilla
Yet another Nagram fork with Telegram official branding.

Based on Telegram 12.1.1 without shitty liquid ass redesign. Intended for personal use with various QoL fixes.

## Changes over [regular Nagram X](https://github.com/risin42/NagramX)
- Telegram official branding over the app (logo, name, icons, splash)
- Enhanced Monet palette generation (more vibrant and contrast)
- Configurable resolution and bitrate for Video Messages
- Backported TLS Client Hello stack from upstream (fixes MTProto for Russian users)
- Hiding stickers/emoji/GIF panel on scroll with "Hide keyboard on Scroll" enabled
- Keep Send as channel available when its hidden; just hold emoji button
- Separate "Disable Swipe to Next..." for Channels and Forums
- Option to disable "Gooey" avatar animation
- "Disable Avatar Blur" feature will UI with pre-12.0.0 profile behaviour (kind of)
- Hide Direct Share button everywhere with "Hide Share Button Next to Post"
- Icons for Attachments Tabs
- Thousands separators for large numbers, also for sub counter when "Disable Number Rounding" enabled
- Hide Stars rating and Gift button from profile
- Changed Nagram's set of by-default enabled features in the sake of being predictable
- Overall UI improvements
  - Fixed abnormal CPU usage caused by PiP source flapping and notification spam (Telegram bug)
  - Fixed centering of online status relatively to user name (Telegram bug)
  - Fixed views counter overlapping number of voters if the poll message forwarded (Telegram bug)
  - Fixed dividers inconsistency over the app
  - Do not convert date in media viewer to numeric format
  - Other tweaks to beautify appearance

## How to compile

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

## Acknowledgments

- [AyuGram](https://github.com/AyuGram/AyuGram4A)
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram)
- [Dr4iv3rNope](https://github.com/Dr4iv3rNope/NotSoAndroidAyuGram)
- [exteraGram](https://github.com/exteraSquad/exteraGram)
- [Nagram](https://github.com/NextAlone/Nagram)
- [Nagram X](https://github.com/risin42/NagramX)
- [Nekogram](https://github.com/Nekogram/Nekogram)
- [OctoGram](https://github.com/OctoGramApp/OctoGram)
