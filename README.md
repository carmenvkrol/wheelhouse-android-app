# WheelHouse — Android app

The Android client from WheelHouse, a personal research project that tests whether an
options wheel strategy can be run by a decision engine. The app is the operator's window
onto that engine: it shows the paper book, the P&L, the risk picture, and the decisions
waiting on a human.

**This repository is an excerpt.** The wider project is private. What's here is the
Android app, its design documents, and a mock API server — enough to build the app, run
it, and read how it was put together, but not the engine behind it.

## What's here

| Path | |
|---|---|
| `android/app/` | The app. Kotlin, Jetpack Compose, single activity, no nav library. |
| `android/design/` | Wireframes, the UI-derived-from-requirements doc, and an inventory of every user flow the app supports. |
| `android/REQUIREMENTS.md` | What the app must do and why. Section 1 is omitted from this copy. |
| `api/mock-server/` | A dependency-free Java HTTP server that serves the API the app expects. |

## What's not here

The decision engine, the backtester, the API contract, and the iOS client all live in the
private repository and aren't reproduced here. The app talks to the mock server instead.

Comments in the source refer to `CONTRACT.md`, `RUBRIC.md` and numbered requirement
sections. Those documents are private; the references are left in place because the code
is carried over unmodified, and they're accurate about why the code does what it does
even when you can't follow them.

## Running it

You need a JDK 17+ and the Android SDK. The system `java` on the original machine was too
old for Gradle, which is why `JAVA_HOME` is set explicitly below — drop it if yours is
current.

Start the mock server:

```bash
cd api/mock-server && ./run.sh
```

It listens on `http://127.0.0.1:8787`. Without it the app falls back to bundled fixtures
and shows an empty book, so start it first if you want to see the decision cards.

Build and install:

```bash
cd android && ./gradlew :app:installDebug
```

On a physical device over USB, forward the port so `127.0.0.1:8787` on the phone reaches
your machine:

```bash
adb reverse tcp:8787 tcp:8787
```

For an emulator, point `ApiConfig.BASE_URL` at `http://10.0.2.2:8787` instead.

## Accessibility

The open pull request on this repository rewrites the decision cards' buttons and the
reasons disclosure so TalkBack announces them correctly. The commit messages explain the
Compose behaviour behind each change — including a `className` precedence rule that
silently discards a semantics `Role` when a node carries both text and a role.

## Credits

Design and code by [Carmen Krol](https://www.linkedin.com/in/carmenvkrol/), with Claude. The requirements and the underlying idea are
[Patrick Dowell's](https://github.com/patrick-dowell); he contributed no code to what's published here.
