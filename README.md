# Takdis an-Nurat (تكديس النورات)

A free, open-source, fully offline Android app that helps you work out how many prayers you owe and keep track of making them up, one prayer at a time.

---

## A word before you begin

*It's the thought that counts.*

Maybe you have regretted the years that slipped by, promising yourself you would start tomorrow, and tomorrow never came. That regret grew heavy. But instead of losing hope, and instead of being led astray by Shaitan's whispers, you decided to take accountability.

Now the time has come. Let this app help you keep track, so that no worldly distraction can steer you away from your commitment. You did not give up in front of Allah, and you promised to earn His pleasure. Let this small work of your brother be a reminder that with every prostration, abundant reward and peace are waiting for you in the afterlife.

---

## What it does

1. **Calculates** how many days of prayers are owed, using two dates:
   - the date puberty began (year required, month optional), and
   - the date you plan to start praying regularly.
2. **Shows the total** for each prayer (Fajr, Zuhr, Asr, Maghrib, Isha, and optionally Witr), either in **days** or in **rakats**. Leap years are handled automatically.
3. **Tracks your progress.** Tap beside a prayer each time you make one up. Mistakes can be undone.
4. **Exports and imports your data** as plain JSON, so you can back it up anywhere you like.

### Planned features

- First-launch setup with a gender choice. Female users can enter haid and nifas days, which are excluded from the total.
- Bulk entry (for example, "+3 Zuhr") and direct editing of a count.
- Home-screen view and a home-screen widget.
- A gentle reminder at an interval you choose (every so many minutes or hours), with a soft sound and a warm message. Quiet hours will be available.
- App lock (PIN, with optional fingerprint).
- A reminder to check with a scholar when setting up.

This app does arithmetic and tracking only. It does not issue rulings, and it contains no verses or fatwas.

---

## Principles

- **Fully offline.** The app asks for no internet permission.
- **No location permission.**
- **No accounts, no analytics, no ads, no tracking.**
- **No Google services.** No Google Play Services, no Firebase.
- **Your data stays on your device** unless you export it yourself.
- **Free forever**, under the GPL-3.0 license.

---

## Design

- **Colors:** only white, gray and black, with their gradients. No red for failure and no green for success.
- **Themes:** full black or full white, chosen by the user.
- **Cards and dialogs:** gray, with a gradient running opposite to the theme.
- **Fonts:** Bebas Neue for card titles and Cooper Hewitt for the text under them. Both are under the SIL Open Font License and are bundled with the app. The app is in English only.
- **Tone:** progress is shown with plain numbers and bar fill, never with guilt.

---

## Technology

- **Language:** Kotlin, native Android.
- **Dependencies:** kept to the minimum needed and fully open source. Nothing proprietary.
- **Storage:** local to the device.
- **Export format:** plain JSON, with a version number so older backups stay readable.
- **Distribution:** GitHub Releases (APK), and F-Droid if accepted.

---

## For developers

Forks are welcome. You are free to clone this repo, change it, and publish your own version, including ports to other platforms such as iOS, as long as you keep it open under the same license.

Contribution guidelines will be added once the project has code.

---

## Disclaimer

This app is a calculator and a counter. It does not give religious rulings. Methods of counting missed prayers, the age of puberty, and related questions can differ between schools of thought. Please check with a qualified scholar from your own school.

---

## License

GPL-3.0. See the `LICENSE` file.
