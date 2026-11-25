# ReelPilot

An Android app for gaming creators: check your own gameplay clips against Meta's 2026 Reels rules, then post them to a **Facebook Page** and **Instagram** with Meta's official Graph API.

## Features
- **Studio**: new Reel, live upload progress, today's posting limits, growth tips.
- **Composer**: picks a video and checks 9:16, resolution, length, frame rate, codecs and size. Includes an originality checklist, a caption builder that flags engagement bait, hashtag suggestions, Facebook scheduling and an AI label.
- **Background uploads**: WorkManager foreground job with a notification. Survives closing the app, retries on network drops and resumes Facebook uploads from the last byte.
- **Auto Pilot**: pick one folder. Every 15 minutes ReelPilot finds new clips, checks them, writes a hook, caption, question and hashtags (templates in English, Hinglish or Hindi, or Claude AI reading 3 frames), then posts at your chosen times with a daily cap.
- **YouTube Shorts**: the same Reel uploads to YouTube through the YouTube Data API (resumable, with title, tags, #Shorts, scheduling and an AI-content label), or with one tap through the YouTube app. API uploads stay private until your Google Cloud project passes YouTube's API audit.
- **Facebook tab**: Page header, followers, recent videos.
- **Instagram tab**: profile, stats, API quota, recent posts grid.
- **Settings**: connect or switch Page, defaults, setup guide.

Tokens are stored with Android's EncryptedSharedPreferences and only sent to `graph.facebook.com` / `rupload.facebook.com`.

## Install
Open the latest release on this repo and download `ReelPilot.apk` on your phone.

## Build
GitHub Actions builds a signed release APK on every push to `main` (see `.github/workflows/build.yml`).
Locally: Android Studio Ladybug or newer, JDK 17, `gradle assembleRelease`.

## Meta API limits
- Facebook Reels: 30 API posts per Page per 24 h, 3–90 s, 9:16.
- Instagram: 100 API posts per 24 h; 5–90 s for the Reels tab. No API scheduling.
