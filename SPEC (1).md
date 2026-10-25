# Takdis an-Nurat: Technical Spec (draft 4)

Android only, Kotlin, GPL-3.0. English only. Fully offline: no internet permission, no location permission, no Google services.

**Design principle:** no one should hit a wall before their first prayer. Every question has a "not sure" path, and nothing is required except dates. A man goes by his memories. A woman goes by her own body and her memories of pregnancy. Schools of thought are optional and can be chosen later.

---

## 1. Calculation rules

### 1.1 Inputs

- `missedFrom`: the date the user started missing prayers. Year required, month optional (January if missing, which leans toward over-counting). Can be later than puberty.
- `startDate`: the day the user begins praying regularly.
- `includeWitr`: on or off. This is its own toggle and is not set by the school choice, because Hanafis count Witr as wajib while the other schools treat it as sunnah.
- `schoolChoice`: see 1.2.
- Female users only: `haid.daysPerMonth` and `lifeEvents` (1.5).

### 1.2 School setting (optional)

| Choice shown to the user | Stored as | Rules used |
|---|---|---|
| Hanafi, Hanbali | `hanafi_hanbali` | Hanafi/Hanbali rules |
| Shafi'i, Maliki | `shafii_maliki` | Shafi'i/Maliki rules |
| I'm not sure | `not_sure` | No school rules (biology and memory) |
| I want to measure with my heart and memories first, then I'll choose later | `memory_first` | No school rules (biology and memory) |

`not_sure` and `memory_first` behave the same. The user can change the choice in Settings at any time, and the totals are recalculated.

| Setting | Hanafi, Hanbali | Shafi'i, Maliki | No school |
|---|---|---|---|
| Haid days allowed per month | 3 to 10 | 1 to 15 | 1 to 15 |
| Monthly haid paused during pregnancy | Yes | No | Yes (periods stop in pregnancy) |
| Nifas limit (days) | 40 | 60 | 60 (input limit only) |

Notes:
- Men's totals do not change with the school.
- In "no school" mode the app trusts what the user enters, within the limits above. The result screen says: "Calculated from your own memories, without a school's rules."
- Monthly haid is also skipped for any month with no bleeding at all (breastfeeding, medical reasons), in every mode.

### 1.3 Period and owed prayers

- The period starts on `missedFrom` (inclusive) and ends on `startDate` (exclusive).
- Days are counted with `java.time.LocalDate`, date-only, no time zones. Leap years are handled automatically.
- Today's date is only used to show how many days remain until `startDate`.
- `daysOwed = periodDays - excludedDays`
- Prayers owed per wakt = `daysOwed` (each of Fajr, Zuhr, Asr, Maghrib, Isha, and Witr if enabled).
- Rakats are derived for display only: Fajr 2, Zuhr 4, Asr 4, Maghrib 3, Isha 4, Witr 3.
- `remaining = max(0, owed - sum of completed counts for that wakt)`
- Saved data stores prayers, never rakats.

### 1.4 Monthly haid deduction

For every calendar month that overlaps the period:
- `monthDays` = days of that month inside the period.
- `pauseDays` = days in `monthDays` covered by a no-haid stretch or by nifas (1.5).
- `eligibleDays` = `monthDays - pauseDays`
- `haidDeduction` = `floor(daysPerMonth × eligibleDays ÷ lengthOfMonth)`

A full month with no pauses deducts exactly `daysPerMonth`. Rounding down keeps partial months on the cautious side.

`excludedDays = sum of haidDeduction + nifas days inside the period`

### 1.5 Life events (female users)

All dates the app estimates lean toward the cautious side: when unsure, the no-haid stretch is longer, so she owes slightly more, never less.

**Birth** (also used for losses after about 20 weeks, stillbirths, twins, C-sections)
- Pregnancy stretch: from 280 days before the birth date until the birth date. Applied only if "monthly haid paused during pregnancy" is Yes for the chosen school.
- After-birth stretch: from the birth date until the first period returned. Always applied.
- Period returned: the user gives a month and year, or "not yet" (the stretch then runs to `startDate`). If "not sure", the app assumes 12 weeks after the birth, or 6 months if she breastfed regularly. When a month is given, the whole month counts as still having no period.
- Nifas: the user gives the number of days, or picks an approximate length (about 2, 3, 4, 5, or 6+ weeks = 14, 21, 28, 35, 42 days). The number is capped at the school's limit. Nifas days are excluded in full, counted from the birth date.

**Pregnancy that ended early**
- `endedOn` month (treated as the last day of that month) and how far along she was: under 4 weeks, 4 to 12, 12 to 20, over 20 (over 20 is entered as a birth).
- Pregnancy stretch: from the upper bound of the chosen range (4, 12 or 20 weeks) before `endedOn` until `endedOn`. Applied only as in the birth rule above.
- After-loss stretch: from `endedOn` until the period returned, or 6 weeks if she isn't sure.
- Confirmation (missed period only, home test, ultrasound, or both) is stored with the entry as her own record. It does not change the numbers.
- Bleeding days: if she says her scholar counts them as nifas, those days are excluded in full (capped at the school's limit). If she says haid, or hasn't asked, nothing extra is excluded and a "check with a scholar" note is shown.

**Another time with no periods**
- Date range (end can be "still ongoing"). Reason is a label only: breastfeeding, medical or hormonal (including birth control), illness or stress, other, not sure. Monthly haid is skipped for these days.

**Overlaps** are merged before counting. Everything is clamped to the period.

### 1.6 Worked examples

**Farid** (male). Missed prayers from 1 Jan 2015. Start: 20 Dec 2026.
- 11 years (3 leap days) = 4,018 days, plus 353 days in 2026 = **4,371 days owed**.
- Rakats: Fajr 8,742. Zuhr 17,484. Asr 17,484. Maghrib 13,113. Isha 17,484. Witr 13,113.

**Fatima** (female), cycle of 7 days, missed prayers from 1 Jan 2018, start 20 Dec 2026. Period: 3,275 days.
- No life events: excluded 753, **owed 2,522 days**.
- With one birth on 10 May 2022, nifas of 40 days, period returned in November 2022:
  - Hanafi, Hanbali (or no school): excluded 681, **owed 2,594 days**. Fajr rakats: 5,188.
  - Shafi'i, Maliki: excluded 746, **owed 2,529 days**. Fajr rakats: 5,058.

These figures come from a prototype of the rules above and should be used as test cases for the app code.

**Estimate helper**
- If the user doesn't remember when prayers stopped, they can enter their birth date and the app suggests the date at 15 lunar years (about 14 years 6 months) as a starting point, which they can edit. A short note says to check with a scholar.

---

## 2. Backup format (JSON)

Plain, human-readable JSON. One file. The PIN and any lock data are **never** exported.

```json
{
  "schemaVersion": 1,
  "app": "takdis-an-nurat",
  "exportedAt": "2026-12-20T09:30:00Z",
  "profile": {
    "gender": "female"
  },
  "setup": {
    "schoolChoice": "memory_first",
    "missedFrom": { "year": 2018, "month": null },
    "startDate": "2026-12-20",
    "includeWitr": true,
    "haid": { "daysPerMonth": 7 },
    "lifeEvents": [
      {
        "type": "birth",
        "date": "2022-05-10",
        "nifasDays": 40,
        "periodReturned": { "year": 2022, "month": 11 },
        "breastfed": null
      },
      {
        "type": "early_loss",
        "endedOn": { "year": 2024, "month": 3 },
        "weeksAlong": "4-12",
        "confirmedBy": "test_and_ultrasound",
        "bleedingDays": 5,
        "bleedingTreatedAs": "unasked",
        "periodReturned": null
      },
      {
        "type": "no_periods",
        "from": "2020-01-01",
        "to": "2020-03-31",
        "reason": "medical"
      }
    ]
  },
  "settings": {
    "displayUnit": "days",
    "theme": "black",
    "reminder": { "enabled": true, "everyMinutes": 300 },
    "quietHours": { "from": "23:00", "to": "06:00" }
  },
  "events": [
    { "id": "3f2a9c1e-0b7d-4c55-9a1e-6d2f8b4e7a10", "wakt": "fajr", "count": 1, "date": "2026-12-21" },
    { "id": "c47e2a90-1d6b-49f3-8e5c-72a0b3d1f6e8", "wakt": "isha", "count": -1, "date": "2026-12-22", "kind": "adjustment" }
  ],
  "summary": {
    "note": "Informational only. Ignored on import.",
    "daysOwed": 2594
  }
}
```

For male users, `haid` and `lifeEvents` are `null` or empty.

**Fields**
- `schoolChoice` is one of `hanafi_hanbali`, `shafii_maliki`, `not_sure`, `memory_first`.
- `wakt` is one of `fajr`, `zuhr`, `asr`, `maghrib`, `isha`, `witr`.
- `events` is the full prayer history. Each tap, bulk add or correction is one event. Undo deletes the event. A negative `count` with `"kind": "adjustment"` is a correction.
- `lifeEvents[].type` is `birth`, `early_loss` or `no_periods`.
- `weeksAlong` is `"under-4"`, `"4-12"` or `"12-20"`.
- `confirmedBy` is `missed_period`, `test`, `ultrasound` or `test_and_ultrasound`.
- `bleedingTreatedAs` is `nifas`, `haid` or `unasked`.
- `reason` is `breastfeeding`, `medical`, `illness_stress`, `other` or `not_sure`.
- Dates use `YYYY-MM-DD`. Month-only values are `{ "year": Y, "month": M }`.
- `summary` is a convenience for people reading the file. The app recalculates everything from `setup` and `events`.

**Import rules**
- Reject files with a missing or higher `schemaVersion`, with a clear message.
- Validate every field (known names, valid dates, whole-number counts, allowed ranges) and refuse the whole file if anything is invalid.
- Offer "Replace everything" or "Merge" (merge skips prayer events whose `id` already exists).
- Ask the user to confirm before replacing anything.

**Privacy note for the export screen**
- The file contains personal information. Show a short reminder to store it somewhere safe, for example an encrypted container.

---

## 3. Screens

Monochrome only: white, gray, black and gradients. Card titles use Bebas Neue (one weight, no added bold). Text under the titles uses Cooper Hewitt, in a medium weight for firm strokes. Both fonts are bundled and licensed under the SIL Open Font License. Wireframes below are simplified.

### 3.1 Welcome (first launch only)

```
┌──────────────────────────────────┐
│                                  │
│   It's the thought that counts.  │
│                                  │
│   (welcome message from README)  │
│                                  │
│             [ Begin ]            │
└──────────────────────────────────┘
```

### 3.2 Setup: profile and lock

```
┌──────────────────────────────────┐
│  Setup                           │
│                                  │
│  Who is this for?                │
│   ( ) Male      ( ) Female       │
│                                  │
│  Protect the app with a PIN?     │
│   [ Set a PIN ]    [ Later ]     │
│                                  │
│  Note: if you forget your PIN,   │
│  it cannot be recovered. Keep    │
│  a backup.                       │
│                                  │
│                      [ Next ]    │
└──────────────────────────────────┘
```

### 3.3 Setup: school of thought (optional)

```
┌──────────────────────────────────┐
│  Which school of thought do you  │
│  follow?                         │
│  You can skip this and change    │
│  it any time.                    │
│                                  │
│   ( ) Hanafi, Hanbali            │
│   ( ) Shafi'i, Maliki            │
│   ( ) I'm not sure               │
│   ( ) I want to measure with my  │
│       heart and memories first,  │
│       then I'll choose later     │
│                                  │
│                      [ Next ]    │
└──────────────────────────────────┘
```

For men this choice changes nothing today. Showing it to everyone is a design decision that can be reversed.

### 3.4 Setup: dates

```
┌──────────────────────────────────┐
│  When did you start missing      │
│  prayers?                        │
│   Year [ 2015 ]  Month [ — ]     │
│   Not sure?  [ Help me estimate ]│
│                                  │
│  When will you start praying     │
│  regularly?                      │
│   Date [ 20 Dec 2026 ]           │
│                                  │
│  [x] Include Witr                │
│                                  │
│  Please check with a scholar.    │
│                                  │
│          [ Back ]   [ Next ]     │
└──────────────────────────────────┘
```

Men go to the result (3.11) after this screen.

### 3.5 Setup (female): monthly cycle

```
┌──────────────────────────────────┐
│  How many days does your period  │
│  usually last?                   │
│                                  │
│      [ − ]     7     [ + ]       │
│                                  │
│  This is set aside from every    │
│  month in your period.           │
│                                  │
│          [ Back ]   [ Next ]     │
└──────────────────────────────────┘
```

The stepper limits follow the school setting (1.2).

### 3.6 Setup (female): pregnancy gate and list

```
┌──────────────────────────────────┐
│  Since 1 Jan 2018, have you      │
│  been pregnant?                  │
│   ( ) Yes        ( ) No          │
│                                  │
│  Your entries                    │
│   Birth, 10 May 2022       [Edit]│
│   No periods, Jan to Mar 2020    │
│                            [Edit]│
│                                  │
│   [ + A birth ]                  │
│   [ + A pregnancy that ended     │
│       early ]                    │
│   [ + Another time with no       │
│       periods ]                  │
│                                  │
│          [ Back ]   [ Next ]     │
└──────────────────────────────────┘
```

"No" skips to the review screen.

### 3.7 Add a birth

```
┌──────────────────────────────────┐
│  A birth                         │
│  Birth date          [ date ]    │
│                                  │
│  How long did the bleeding after │
│  birth last?                     │
│   Days [ 40 ]  or [ I don't      │
│   remember ]                     │
│   If not remembered, about:      │
│   (2 wks) (3) (4) (5) (6+)       │
│                                  │
│  Has your period come back?      │
│   ( ) Yes, around [ month year ] │
│   ( ) Not yet                    │
│   ( ) Not sure                   │
│   If not sure: did you           │
│   breastfeed regularly?          │
│   ( ) Yes   ( ) No               │
│                                  │
│          [ Cancel ]  [ Save ]    │
└──────────────────────────────────┘
```

### 3.8 Add a pregnancy that ended early

```
┌──────────────────────────────────┐
│  A pregnancy that ended early    │
│  About when did it end?          │
│   [ month year ]                 │
│  How far along were you?         │
│   (under 4 wks) (4 to 12)        │
│   (12 to 20) (over 20)           │
│  How did you confirm it?         │
│   ( ) Missed period only         │
│   ( ) Home test                  │
│   ( ) Ultrasound                 │
│   ( ) Test and ultrasound        │
│  How many days did the bleeding  │
│  last?  [ 5 ] or [ don't recall ]│
│  What does your scholar say      │
│  about that bleeding?            │
│   ( ) Nifas ( ) Haid             │
│   ( ) I haven't asked            │
│                                  │
│          [ Cancel ]  [ Save ]    │
└──────────────────────────────────┘
```

Choosing "over 20" switches to the birth screen.

### 3.9 Add another time with no periods

```
┌──────────────────────────────────┐
│  A time with no periods          │
│  From [ month year ]             │
│  To   [ month year ] or          │
│       [x] still ongoing          │
│  Why?                            │
│   ( ) Breastfeeding              │
│   ( ) Medical or hormonal        │
│   ( ) Illness or stress          │
│   ( ) Other   ( ) Not sure       │
│                                  │
│          [ Cancel ]  [ Save ]    │
└──────────────────────────────────┘
```

### 3.10 Review (female)

```
┌──────────────────────────────────┐
│  Please review                   │
│                                  │
│  No-haid stretches               │
│   Aug 2021 → Nov 2022  Pregnancy │
│                         and birth│
│                            [Edit]│
│   Jan 2020 → Mar 2020  Medical   │
│                            [Edit]│
│  Nifas: 40 days from 10 May 2022 │
│                            [Edit]│
│                                  │
│  Total set aside: 681 days       │
│                                  │
│  These are estimates. Please     │
│  check with a scholar.           │
│                                  │
│          [ Back ]   [ Done ]     │
└──────────────────────────────────┘
```

### 3.11 Calculator result

```
┌──────────────────────────────────┐
│  Your total                      │
│  1 Jan 2018 → 20 Dec 2026        │
│  Period: 3,275 days              │
│  Set aside (haid, nifas): 681    │
│  Owed: 2,594 days                │
│                                  │
│  [ Days | Rakats ]               │
│                                  │
│  Fajr      2,594                 │
│  Zuhr      2,594                 │
│  Asr       2,594                 │
│  Maghrib   2,594                 │
│  Isha      2,594                 │
│  Witr      2,594                 │
│                                  │
│  Calculated from your own        │
│  memories, without a school's    │
│  rules.                          │
│                                  │
│  [ Edit ]        [ Start ]       │
└──────────────────────────────────┘
```

The "Set aside" line appears only for female users. The last note appears only when no school is chosen. When a school is chosen, it names the school group instead.

### 3.12 Tracker (home)

```
┌──────────────────────────────────┐
│  Remaining       [ Days | Rakats]│
│                              ⋮   │
│ ┌──────────────────────────────┐ │
│ │ Fajr                   2,594 │ │
│ │ ░░░░░░░░░░░░░░░░░░░░░░░  0%  │ │
│ │                      [ +1 ]  │ │
│ └──────────────────────────────┘ │
│   ... Zuhr, Asr, Maghrib, Isha,  │
│   Witr                           │
│                                  │
│  [ History ]        [ Settings ] │
└──────────────────────────────────┘
```

- **Tap +1** records one prayer. A short bar at the bottom offers **Undo** for a few seconds.
- **Long-press** on a card opens the bulk-entry dialog.
- The ⋮ menu holds Export, Import and History.
- Progress is shown only as bar fill and a percentage. No color change.

### 3.13 Bulk entry

```
┌──────────────────────────────────┐
│  Fajr: add qada                  │
│                                  │
│   [ − ]    [  3  ]    [ + ]      │
│   Date: today                    │
│                                  │
│  Already made some up before?    │
│   Set remaining to [ _____ ]     │
│                                  │
│         [ Cancel ]  [ Add ]      │
└──────────────────────────────────┘
```

"Set remaining" records one adjustment event that brings the remaining count to the number entered.

### 3.14 Lock screen

```
┌──────────────────────────────────┐
│          Enter your PIN          │
│            ○ ○ ○ ○               │
│          [1] [2] [3]             │
│          [4] [5] [6]             │
│          [7] [8] [9]             │
│          [👆] [0] [⌫]            │
└──────────────────────────────────┘
```

**Behavior**
- PIN of 4 to 8 digits. Stored only as a salted hash on the device.
- Optional fingerprint unlock (Android's built-in prompt).
- The app locks again after a short time in the background (the user chooses the delay).
- Repeated wrong PINs add growing delays.
- With the lock on, the app's preview in the recent-apps screen is hidden and screenshots are blocked.
- There is no recovery for a forgotten PIN, because there are no accounts. The setup note and the backup reminder exist for this reason.

### 3.15 Home-screen widget

```
┌──────────────────────────────┐
│ Fajr         2,594     [ +1 ]│
│ Zuhr         2,594     [ +1 ]│
│ Asr          2,594     [ +1 ]│
│ Maghrib      2,594     [ +1 ]│
│ Isha         2,594     [ +1 ]│
└──────────────────────────────┘
```

**Protection against accidental taps**
- Tapping +1 gives a short vibration, and that row briefly changes to "Added · Undo" for about 5 seconds.
- Tapping the wakt name (not the button) opens the app.
- The +1 buttons are small but spaced apart.

**With the app lock on**
- The widget shows only a lock symbol instead of numbers (setting, on by default), and the +1 buttons are disabled.
- Tapping the widget opens the lock screen.

### 3.16 Settings

- Theme: Full black or full white
- Display: Days or Rakats
- Include Witr
- School of thought
- Reminders: on or off, interval chosen by the user in minutes or hours (minimum 15 minutes), quiet hours
- App lock: PIN, fingerprint, lock delay
- Export, Import
- Edit dates, cycle details and life events
- About: version, license, source link

---

## 4. Reminders

- The user picks the interval: every X minutes or X hours, with a minimum of 15 minutes (Android does not reliably run background tasks more often). The default is every 5 hours. Each reminder has a soft sound and a warm line asking whether any qada is left to pray today.
- Quiet hours (default 23:00 to 06:00).
- Notification shows no counts and hides its content on the lock screen. Android may delay the exact timing, and some phone makers stop background tasks aggressively. The settings screen includes a short tip about turning off battery optimization for the app.
- Uses only Android's standard notification and scheduling system.

---

## 5. Open decisions

1. **App icon.** The name is decided: Takdis an-Nurat.
2. **Soft warning** for very early start dates, or none.
3. **School screen for men:** keep it (current decision) or hide it, since it changes nothing for them yet.
4. **Review by a scholar** of the school rules table (1.2) and the estimate defaults (1.5) before release.
