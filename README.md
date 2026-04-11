# Nagram X Vanilla
Yet another Nagram fork with Telegram official branding.

## How to compile

### Locally

1. Obtain API credentials (`TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH`) from [Telegram Developer Portal](https://my.telegram.org/auth). Create `local.properties` in the project root with:

   ```properties
   TELEGRAM_APP_ID=<your_telegram_app_id>
   TELEGRAM_APP_HASH=<your_telegram_api_hash>
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
   KEYSTORE_PASS=<your_keystore_password>
   ALIAS_NAME=<your_alias_name>
   ALIAS_PASS=<your_alias_password>
   TELEGRAM_APP_ID=<your_telegram_app_id>
   TELEGRAM_APP_HASH=<your_telegram_api_hash>
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
