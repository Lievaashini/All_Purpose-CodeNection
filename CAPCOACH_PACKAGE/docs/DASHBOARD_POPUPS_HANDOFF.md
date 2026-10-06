# Dashboard pop-ups - logic handoff

Owner of this document: UI/UX. **No Java was edited to produce it.**

The dashboard's three pop-ups — **Rebalance**, **Daily pop-up (Daily Harvest)** and
**Weekly pop-up (Weekly Feast)** — used to be opened by tapping a button on the
"Simulation" banner in `fragment_dashboard.xml`. That bar is now hidden, because all three
are pop-ups: they should appear when the app decides it is the right moment, not when the
user goes looking for a button.

This document is the contract between the UI side and whoever writes that trigger logic.
It lists exactly what exists, exactly what to call, and exactly what is still missing.

---

## 1. What changed on the UI side

| File | Change |
|---|---|
| `app/src/main/res/layout/fragment_dashboard.xml` | The "Simulation" banner is now `android:visibility="gone"` and carries a **new** id, `@+id/simulationBanner`. |
| `docs/DASHBOARD_POPUPS_HANDOFF.md` | This file (new). |

Deliberately **not** changed:

- **No Java file was touched.** `DashboardFragment` is byte-for-byte as it was.
- The bar was **hidden, not deleted**. All three button ids are still in the layout and
  still wired by `DashboardFragment.setupActions()` (`DashboardFragment.java` lines
  928–937). A `GONE` parent still inflates its children, so those `findViewById()` calls
  still resolve and nothing throws. Deleting the ids instead would have silently turned
  all three of those wires into no-ops.
- `@id/rebalanceToast` is **not** part of this bar and is **not** hidden by this change.
  It stays `visibility="gone"` in the layout and is faded in only after the user accepts
  the rebalance proposal.

### ⚠️ Read this before you run the app

Hiding the bar removes the **only** way to open these three pop-ups. Until the trigger
logic in section 4 is written, the Rebalance sheet, Daily Harvest and Weekly Feast are
**unreachable from the dashboard**. That is the intended handoff state, not a bug — but it
means a reviewer who installs the current build will not see any of them.

### Bringing the bar back temporarily (demos, screenshots)

Screen *previews* in Android Studio still show it: the banner keeps
`tools:visibility="visible"`, which is design-time only. On a real device, un-hide it with:

```java
requireView().findViewById(R.id.simulationBanner).setVisibility(View.VISIBLE);
```

---

## 2. Pop-up inventory

| # | Pop-up | UI class | Layout | How it is presented **today** | What logic must supply |
|---|---|---|---|---|---|
| 1 | Rebalance | `ui/dashboard/TriageSheetFragment` | `sheet_triage.xml` | `BottomSheetDialogFragment` — already a real pop-up | The decision to show it |
| 2 | Daily pop-up | `ui/harvest/DailyHarvestFragment` | `fragment_daily_harvest.xml` | **Full-screen** fragment replace | Trigger **+** a decision to re-present it as a pop-up |
| 3 | Weekly pop-up | `ui/harvest/FeastFragment` | `fragment_feast.xml` | **Full-screen** fragment replace | Trigger **+** a decision to re-present it as a pop-up |

Only #1 is a pop-up by construction. #2 and #3 are authored as full screens and are swapped
over the whole content container — see section 3.2, which is the one open design question
this handoff cannot answer on its own.

---

## 3. The exact calls

### 3.1 Rebalance pop-up — already a bottom sheet ✅

The working call is `DashboardFragment.openTriage()`
(`app/src/main/java/com/example/codenection2026_package/ui/dashboard/DashboardFragment.java`
lines 1089–1093):

```java
private void openTriage() {
    TriageSheetFragment sheet = new TriageSheetFragment();
    sheet.setOnRebalanceAccepted(this::showRebalanceToast);
    sheet.show(getChildFragmentManager(), "triage");
}
```

Two things to know when you call it:

1. **It is `private`.** From outside `DashboardFragment` you must either widen it or add a
   thin public wrapper on the fragment:

   ```java
   /** Opens the rebalance proposal as a pop-up. */
   public void showRebalancePopup() {
       openTriage();
   }
   ```

2. **The callback must be set before `show()`.** `setOnRebalanceAccepted(...)` is the only
   thing that makes the "Schedule Rebalanced" notice appear — the sheet dismisses itself
   and then fires the listener (`TriageSheetFragment.java` lines 100–106). Show the sheet
   without that listener and accepting it will look like nothing happened.

Also available: `TriageSheetFragment.TAG` is a public constant equal to `"triage"`
(`TriageSheetFragment.java` line 49) — prefer it over the literal, and use it to avoid
stacking two sheets:

```java
if (getChildFragmentManager().findFragmentByTag(TriageSheetFragment.TAG) == null) { ... }
```

The sheet has **no data dependency**: every row is a fixed proposal, so it renders even
before the Room layer can supply real numbers.

### 3.2 Daily pop-up — currently a full screen

The public call already exists
(`app/src/main/java/com/example/codenection2026_package/ui/shell/ScreenNav.java` lines 62–64):

```java
ScreenNav.showDailyHarvest(this);   // `this` = any added Fragment
```

It is `public static` and safe to call from anywhere that holds an added `Fragment`, e.g.
from inside `DashboardFragment`:

```java
ScreenNav.showDailyHarvest(DashboardFragment.this);
```

What it actually does (`ScreenNav.replace`, lines 96–106) is
`replace(R.id.nav_host_container, ...).addToBackStack("daily_harvest")` — a **full-screen
swap of the whole content area**, not a dialog. Existing call sites you can copy the
pattern from: `DashboardFragment` line 930 (the now-hidden button),
`CompanionFragment` line 283, `VoiceDinoDialogFragment` line 319.

**If it must genuinely be a pop-up**, that is a presentation change and it is not free.
`fragment_daily_harvest.xml` is authored as a full screen: it carries the top content
inset, its own theme toggle, its own bottom navigation and a 144dp bottom pad. Dropping it
into a `DialogFragment` without a re-layout will render a full-screen page inside a small
window. Two behaviours also have to be redirected, because the fragment navigates by
replacing the dashboard underneath it:

- `launchButton`, and a second tap on "Collect", call `ScreenNav.showDashboard(this)`
  (`DailyHarvestFragment.java` lines 348 and 363).
- `skipHarvest()`'s confirm dialog calls `ScreenNav.showDashboard(this)` (line 470).

Inside a dialog those should dismiss the dialog instead, or the user loses the dashboard
they came from. A dialog wrapper that intercepts these with a dismiss callback is the clean
route. **UI/UX input is needed on the dialog size** — that is a design decision, not a
logic one.

### 3.3 Weekly pop-up — currently a full screen

Same story
(`ScreenNav.java` lines 66–68):

```java
ScreenNav.showFeast(this);
```

Full-screen replace onto the back stack with tag `"feast"`. Existing call sites:
`DashboardFragment` line 931 (hidden button), `CompanionFragment` line 286,
`VoiceDinoDialogFragment` line 323.

Same dialog caveat: `closeButton` and `doneButton` both call
`ScreenNav.showDashboard(this)` (`FeastFragment.java` lines 177 and 181).

---

## 4. What the logic owner still has to write

### 4.1 Suggested trigger conditions

These are proposals, not existing behaviour. Nothing below is implemented yet.

| Pop-up | Condition to propose | Evidence already in the codebase |
|---|---|---|
| Rebalance | Today's load reaches the crash ceiling, or the week contains an overloaded day; at least one task is shedable | `DashboardFragment.CRASH_CEILING_PERCENT = 90` (line 99); `LoadShedder.predictTaskAction(...)` returns `MOVE` for tasks safe to postpone (`engine/LoadShedder.java`) |
| Daily pop-up | Once per day, on the first dashboard entry after the morning, when at least one task was completed yesterday | `DailyHarvestFragment.APPLES_GROWN` is a hardcoded `4` (line 61); its own comment names this as the value Role 1's completed-task query must supply |
| Weekly pop-up | Once per week, on Sunday, when the week's stash has reached the feast goal | `FeastFragment.FEAST_DAY = 7` (line 54), `TOTAL_APPLES = 16` (line 56); `DailyHarvestFragment.FEAST_GOAL = 20` (line 48) holds the same goal from the other screen |

### 4.2 Where to put the trigger

`DashboardFragment.onViewCreated()` is the natural hook — it already binds every view and
ends with `reloadTasks()` (line 201):

```java
setupFilters();
setupActions(view);
reloadTasks();
maybeShowPopups();   // <-- add after this line
```

Three guards are needed:

- **`isAdded()`** before touching the child fragment manager.
- **`savedInstanceState == null`**, so rotating the device does not re-fire a pop-up that
  was already shown or dismissed.
- **At most one pop-up per dashboard entry.** Suggested priority: Daily → Weekly →
  Rebalance, since Daily is time-boxed to the morning.

### 4.3 Persisting "already shown"

There is **no** flag for this yet. `OnboardingPrefs`
(`ui/onboarding/OnboardingPrefs.java`) is the existing `SharedPreferences` wrapper and all
of its keys are `private static final`, so a new key has to be added there, e.g.
`KEY_LAST_DAILY_POPUP_DATE` / `KEY_LAST_WEEKLY_POPUP_WEEK`, with matching getters and a
`save...` method following the file's existing style. Its javadoc notes it is itself a
placeholder for Role 1's Room layer.

Do not skip this: a trigger with no persisted flag will fire the pop-up on every single
dashboard entry, including every Back navigation, because `ScreenNav.back()` re-creates the
dashboard.

### 4.4 Two things to keep intact

- **Keep the three button ids** (`rebalanceButton`, `dailyPopupButton`,
  `weeklyPopupButton`) in the layout, and keep `setupActions()` wiring them.
  `DashboardFragment` resolves all three by id; removing them turns those wires into
  silent no-ops.
- **Keep the `"triage"` tag** for the rebalance sheet, and do not show two sheets under it
  at once.

---

## 5. Reference table

| Thing | Location |
|---|---|
| Hidden bar (container) | `fragment_dashboard.xml`, `@id/simulationBanner` |
| Rebalance button | `fragment_dashboard.xml`, `@id/rebalanceButton` |
| Daily pop-up button | `fragment_dashboard.xml`, `@id/dailyPopupButton` |
| Weekly pop-up button | `fragment_dashboard.xml`, `@id/weeklyPopupButton` |
| Button wiring | `DashboardFragment.java`, `setupActions()`, lines 928–937 |
| Rebalance sheet opener | `DashboardFragment.java`, `openTriage()`, lines 1089–1093 |
| Rebalance notice | `DashboardFragment.java`, `showRebalanceToast()`, line 1144 |
| Triage sheet | `ui/dashboard/TriageSheetFragment.java` (`TAG = "triage"`) |
| Daily Harvest screen | `ui/harvest/DailyHarvestFragment.java` |
| Weekly Feast screen | `ui/harvest/FeastFragment.java` |
| Navigation helper | `ui/shell/ScreenNav.java` (`showDailyHarvest` 62, `showFeast` 66) |
| Rebalance decision model | `engine/LoadShedder.java` |
| Prefs wrapper | `ui/onboarding/OnboardingPrefs.java` |
