# Moneyment

An Android app to log the money you spend and earn. One person, one phone:
no account, no server. The app itself only contacts GitHub, to check for and
download a new version, and sends none of your data. Android's own backup may
copy the app's data to your Google account if you turned that on, and exports
only go where you choose.

- Log an Expense or an Income in a few taps, with a Category and an Account.
- Review each month's Transactions, filtered by Category or Account.
- Charts: Expenses by Category, and Income against Expenses over 12 months.
- Backup to a JSON file you can restore, and export to CSV.
- English and Spanish, light and dark.

The vocabulary used in the app and the code is in [GLOSSARY.md](GLOSSARY.md).
Design decisions are in [docs/adr](docs/adr).

## Install

Download the APK from the [latest release](https://github.com/vieitesss/moneyment/releases/latest)
and open it on the phone. From then on the app offers new versions itself:
Settings, then **Check for updates**, downloads the new APK and the phone asks
you to install it. The app itself only contacts GitHub, to check for and
download a new version, and sends none of your data. Android's own backup may
copy the app's data to your Google account if you turned that on, and exports
only go where you choose.
[Obtainium](https://obtainium.imranr.dev) still works if you prefer it.

Installing a newer APK over an older one keeps your data.

## Build

Requirements: JDK 21 and the Android SDK (platform 37). No IDE is needed.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
echo "sdk.dir=/path/to/android/sdk" > local.properties

./gradlew testDebugUnitTest   # unit tests
./gradlew installDebug        # build and install on a connected device or emulator
```

With [just](https://just.systems) and an emulator named `moneyment`:

```sh
just test            # unit tests
just update          # start the emulator if needed, install the signed release APK over the current one, open the app
just emulator-stop   # stop the emulator
```

`just update` installs the release build because a debug APK cannot replace a
release one: they are signed with different keys.

## Release

`scripts/release.sh` builds the signed APK and publishes it as a GitHub
Release. It reads the signing key from `~/.android/moneyment-keystore.properties`:

```properties
storeFile=/path/to/moneyment-release.jks
storePassword=...
keyAlias=moneyment
keyPassword=...
```

Bump `versionCode` and `versionName` in `app/build.gradle.kts` first. Use
`scripts/release.sh --build-only` to build without publishing, then install
with `adb install -r build/moneyment-<version>.apk`.

## License

[GPL-3.0](LICENSE)
