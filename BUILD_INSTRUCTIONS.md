# Pardhan Player v4 — Build Notes

- Package: `com.pardhancodes.pardhanplayer`
- Version: `4.0.0` (versionCode 400)
- Launcher icon: custom Pardhan Player vector logo (`res/drawable/pardhan_player_logo.xml`)
- Build: `gradle assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## V4 additions
- External `.srt` and `.vtt` subtitle picker in the video player. Select Subtitle while a video is playing.
- Compact mini playback bar above bottom navigation, with open, play/pause and close controls.
- Player swipe gestures: vertical swipe on left half adjusts screen brightness; right half adjusts media volume.
- Existing v3 features retained: media library, folders, network streams, playback resume, PiP, equalizer, audio boost, private vault, settings backup/restore.

## Notes
- The project source is supplied. Build/sign the APK in Android Studio or a compatible Gradle + Android SDK environment.
- The current chat runtime does not include an Android SDK, so an APK build has not been verified here.

## GitHub Actions
A workflow is included at `.github/workflows/build-apk.yml`. Upload the project contents to the repository root and push to `main`, or run it manually from the Actions tab. The generated debug APK is available as the `Pardhan-Player-V4-APK` artifact.
