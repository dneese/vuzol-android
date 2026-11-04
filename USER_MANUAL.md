# Vuzol — User Manual

**Version 1.7.1**

---

## Table of Contents

1. [Overview](#1-overview)
2. [Requirements](#2-requirements)
3. [Getting Started](#3-getting-started)
4. [The Three Communication Modes](#4-the-three-communication-modes)
5. [Messaging](#5-messaging)
6. [Voice Notes](#6-voice-notes)
7. [Photos, Videos & Files](#7-photos-videos--files)
8. [Location Channels](#8-location-channels)
9. [Identity & Verification](#9-identity--verification)
10. [Privacy & Security](#10-privacy--security)
11. [Settings](#11-settings)
12. [Emergency Data Deletion](#12-emergency-data-deletion)
13. [Troubleshooting](#13-troubleshooting)
14. [Permissions Reference](#14-permissions-reference)

---

## 1. Overview

Vuzol is a decentralized, privacy-focused peer-to-peer messaging application. It works without relying on any central server and supports three distinct communication modes:

- **Offline Bluetooth Mesh** — Talk to nearby people with no internet at all.
- **Location Channels** — Connect with people in your geographic area over the internet.
- **Nostr Protocol** — Internet-based encrypted messaging via open relays.

All private messages are end-to-end encrypted using the Noise protocol with Curve25519 key exchange and ChaCha20-Poly1305 encryption. No account registration or phone number is required.

---

## 2. Requirements

- Android 9 (API 28) or later
- A device with Bluetooth Low Energy (BLE) support
- Location services enabled (required by Android for BLE scanning)
- Internet connection (optional — only needed for Location Channels and Nostr modes)

---

## 3. Getting Started

### First Launch & Onboarding

When you open Vuzol for the first time, the app will guide you through a short setup:

1. **Bluetooth Check** — The app verifies your device supports Bluetooth Low Energy. If Bluetooth is off, you will be prompted to enable it.
2. **Location Permission** — Android requires location permission for BLE scanning. Grant precise location access when prompted.
3. **Background Location** *(optional)* — Granting background location improves mesh reliability when the app is in the background.
4. **Battery Optimization** — You will be asked to disable battery optimization for Vuzol. This allows the mesh service to run persistently in the background and receive messages while the screen is off.
5. **Notification Permission** — Allow notifications to be alerted about incoming messages.
6. **Initialization** — The app starts the mesh network. A loading screen shows progress. Once complete, you are taken to the main chat screen.

> **Tip:** Skipping the battery optimization step means the app may stop running in the background and miss messages or disconnect from the mesh.

---

## 4. The Three Communication Modes

### Bluetooth Mesh (Offline)

This mode requires no internet. Your device discovers nearby peers automatically using Bluetooth Low Energy advertising and scanning. Messages are relayed hop-by-hop through other mesh participants, extending the effective range beyond direct Bluetooth reach.

- Works indoors, underground, in areas with no signal.
- Range per hop: approximately 10–30 metres (typical BLE range).
- Messages are encrypted end-to-end between sender and recipient.
- The mesh self-heals — if a node disconnects, traffic reroutes automatically.

### Location Channels (Online)

Location channels let you join public topic rooms tied to a geographic area. Each area is identified by a **geohash** — a short code representing a region on the map, from a city block up to a whole country.

- Requires an internet connection.
- Messages are relayed via Nostr protocol relays.
- Your exact GPS position is never transmitted — only the coarse geohash you choose.

### Nostr Protocol (Online)

Nostr is an open, censorship-resistant messaging protocol. Vuzol connects to multiple public Nostr relays simultaneously. Messages published to your Nostr identity (npub) are signed with your Ed25519 key.

---

## 5. Messaging

### Viewing Conversations

The main screen shows a list of active conversations — peers discovered via the Bluetooth mesh or Nostr channels. Tap any conversation to open it.

### Sending a Message

1. Tap a conversation to open the chat view.
2. Type your message in the input field at the bottom.
3. Tap **Send** (or press the send button).

Private messages are encrypted before leaving your device. The recipient sees the message only after it is decrypted with their key.

### Message Status

- Messages sent over the mesh use a store-and-forward mechanism. If the recipient is not currently reachable, your message is stored and delivered when they reconnect.
- A gossip sync protocol runs in the background to fill any gaps caused by temporary disconnections.

### Blocking a User

1. Open the conversation with the user.
2. Tap the user's name or avatar in the chat header.
3. In the **User Options** sheet, tap **Block**.

Blocked users cannot send you messages and will not appear in your peer list.

### Replying, Reacting and Forwarding

- **Reply** — swipe a message to the right, double-tap it, or long-press → **Reply**.
- **React** — long-press a message and pick an emoji; reactions appear under the bubble.
- **Forward** — long-press → **Forward** and pick another channel.
- **Save** — long-press → **Save**; the message is stored in **Saved Messages** on the home screen.
- **Edit** — long-press one of your own messages → **Edit**. The new text is broadcast to the channel (Nostr kind 20004) and marked as *edited*.
- **Delete** — swipe your own message to the left, or long-press → **Delete**.

### Message Effects

Sending a single emoji (❤️, 🔥, 👍, 😍, 🎉, 😂, 💯, 🚀, 👏, 🙏) triggers a small
animation in the chat.

### Auto-delete (Disappearing Messages)

1. Open a location channel and long-press any message.
2. Tap **Auto-delete messages**.
3. Pick a lifetime: off, 2 seconds, 1 hour, 24 hours or 7 days.

Messages older than the selected lifetime are removed from the chat automatically.

### Chat Appearance

Tap the **⋮** button in the chat header and choose **Appearance** to change:

- **Chat color** — one of eight accent colors used for the header, your bubbles and the send button.
- **Wallpaper** — solid, soft gradient, or your own photo (picked from the gallery).
- **Pattern** — a vector pattern drawn behind the chat: dots, geometry, waves, hexagons, circuit or topography.

### Message Menu

Long-press a message to open the **message actions sheet** — one place for every action:

- Quick reactions (👍 ❤️ 😂 😮 😢 🔥)
- **Reply**, **Copy**, **Share**, **Forward**, **Save**, **Pin**, **Edit**, **Select**, **Details**, **Mute**, **Auto-delete**, **Block**, **Delete**
- **Details** opens a sheet with the message id, sender, time, delivery status and PoW difficulty.

Swipe a message right to reply, swipe left on your own message to delete it.

### Markdown in Messages

Inside chat text you can use light formatting:

| Markup | Result |
|---|---|
| `**text**` | bold |
| `*text*` or `_text_` | italic |
| `` `text` `` | monospace code |
| `~~text~~` | crossed out |

### Your Profile

Tap your avatar in the top-left corner of the home screen (or the **Profile** card in Settings):

- Pick a photo from the gallery — it stays on your device and is shown next to your messages in group chats.
- Change your **nickname**, **display name** and **bio**.
- Press **Publish to Nostr** to sign and publish a NIP-01 metadata event (kind 0) with your name and bio.

### Away Mode

- Type `/brb` (optionally `/brb at the shop`) in any chat to mark yourself as away.
- A banner appears above the input; tap it to cancel.
- Type `/back` to clear the status.
- While away, mentions are answered automatically with your away text (can be switched off in settings).

### Notifications

- Notifications support an inline **Reply** field and a **Mark as read** action.
- Sound, vibration and per-channel mute can be changed in **Settings → Notifications**.
- Four notification sound packs: **System**, **Beep**, **Pop**, **Chime**.

### Home Screen

- Bottom navigation: **Chats / People / Settings**.
- Use the search field to find chats and contacts quickly.
- Long-press a chat and choose **Pin to top** to keep it in the pinned strip.
- **Saved Messages** holds every message you bookmarked.

---

## 6. Voice Notes

Voice notes let you record and send short audio messages with a visual waveform display.

### Recording

1. In the chat input bar, **long-press the microphone button**.
2. Speak your message. A real-time waveform animates as you record.
3. **Release** the button to send the recording automatically.
4. To cancel, **slide your finger away** from the microphone button before releasing.

### Playback

- Received voice notes display as a waveform.
- Tap the **play button** to listen.
- Tap anywhere on the waveform to seek to that point in the audio.
- A blue fill progresses across the waveform during playback.

> Voice notes are transmitted using the same encrypted packet protocol as text messages and are fragmented automatically if they exceed the BLE MTU size.

---

## 7. Photos, Videos & Files

### Sending Photos, Videos and Files

1. In the chat input bar, tap the **+ (attachment) button**.
2. Choose **Photo or video**, **File**, or **Multiple files**.
3. Photos and videos are resized to a maximum of 1600 px on the longest edge and compressed as JPEG (quality 90) before sending; other files are sent as-is.
4. The maximum size of a single attachment is **50 MB** — larger files are rejected with a clear message.
5. A block-reveal animation shows upload progress on the sender's side.

### Sending Several Files at Once

- Select up to **20** photos/videos, or several documents, and they are sent as **one message**.
- The message shows a compact grid (up to four previews) with a `+N` badge for the rest.
- Tap a photo in the grid to open it full screen, tap a video to play it right in the chat.

### Receiving Photos and Videos

- As an image arrives, it reveals progressively using a 24 × 16 grid animation.
- Tap the image thumbnail to open it **full screen**.
- **Zoom:** pinch with two fingers, or double-tap to zoom in and out; drag to pan while zoomed.
- In the full-screen view, tap **Save** to download the image to your device's Downloads folder.
- Videos arrive as a thumbnail with a play button — tap it to play in an embedded player:
  - **play / pause** button,
  - **seek bar** with the current time and the total duration — drag it to jump anywhere in the video,
  - **fullscreen** button (rotates the phone to landscape),
  - **Save** to store the video on the device.

### Media Gallery

1. Open a chat and tap the **⋮** button in the top bar.
2. Choose **Gallery** to see every photo **and video** shared in that conversation.
3. Tap a photo to open it full screen (with zoom), tap a video to play it.

### Saving a File

- Open the **⋮** menu on any file message and choose **Save to device** — the file is written to your Downloads folder.

---

## 8. Location Channels

Location channels are public chat rooms tied to geographic areas. Anyone who joins the same geohash channel can read and send messages there.

### Opening Location Channels

1. Tap the **Location Channels** button (map pin icon) on the main screen.
2. The **Location Channels sheet** opens, showing channels near your current location.

### Joining a Channel

1. Browse the list or tap **Pick on Map** to open the interactive map.
2. Zoom in or out to select the geohash precision level you want:
   - **Fine** (block/street level)
   - **Medium** (neighbourhood)
   - **Coarse** (city / province / region)
3. Tap a geohash cell on the map to select it.
4. Tap **Join Channel** to subscribe.

### Location Notes

Location notes are persistent messages attached to a specific geohash that anyone passing through that area can read.

1. Open a location channel.
2. Tap **Location Notes**.
3. Tap **Add Note** and type your message.
4. The note is stored and visible to future visitors of that channel.

> **Privacy reminder:** Do not screenshot location channel screens, as screenshots may reveal your approximate location.

---

## 8.1 Group Invites

You can invite people into a private group chat with a code or a link.

1. Open the group chat and tap the **⋮** button in the top bar.
2. Choose **Invite friends** — the invite sheet opens with a QR code, the short invite code and a web link.
3. Send the invite any way you like:
   - **Share** — sends the text with the code and the link to any messenger;
   - **Copy link** — copies `https://dneese.github.io/vuzol-android/?code=…`;
   - **Copy code** — copies just the code;
   - **Open in Vuzol** — opens the invite directly in the app.
4. The web link works in **any browser**: it shows the group code, offers to open Vuzol and, if the app is not installed yet, offers the **APK download** for your device (ARM64, universal, x86_64).
5. The QR code contains the same web link, so scanning it with any camera opens the invite page.

> The recipient can also paste the code (or the whole link) into the **Join group** dialog inside Vuzol.

### Growing the circle

- In **Settings → Share Vuzol with friends** you can send the download link to anyone — the landing page picks the right APK automatically.
- Invites inside Vuzol are one tap away: ⋮ → **Invite friends**.

---

## 9. Identity & Verification

### Your Identity

Vuzol generates a cryptographic key pair automatically on first launch. Your identity is your **public key** (displayed as a Nostr npub address). No username or password is needed.

### Verifying a Peer

Verification confirms that the public key you see in a conversation genuinely belongs to the person you are talking to — preventing impersonation.

**To verify a peer:**

1. Open the conversation with the peer.
2. Tap their name or avatar → **View User** → **Verify**.
3. You will see your **fingerprint** and the peer's **fingerprint**.
4. Compare fingerprints with the peer in person, over a phone call, or any trusted out-of-band channel.
5. Alternatively, scan the peer's **QR code** (or ask them to scan yours) using the **QR Scanner** in the verification screen.
6. Once fingerprints match, tap **Mark as Verified**.

After mutual verification:
- A system message appears in the chat confirming verification.
- The chat header shows **Encrypted & Verified**.

---

## 10. Privacy & Security

| Feature | Details |
|---|---|
| **Encryption** | Noise protocol (Noise_NN), Curve25519 key exchange, ChaCha20-Poly1305 AEAD |
| **Signing** | Ed25519 digital signatures on all packets |
| **No central server** | Mesh mode is fully serverless |
| **No account required** | Keys generated locally; no phone number or email |
| **Location privacy** | Only coarse geohash shared; exact GPS never transmitted |
| **Tor support** | Optional — route all internet traffic through the Tor network |
| **Emergency wipe** | Triple-tap the app title to delete all data instantly |

### Enabling Tor

1. Open **Settings** (gear icon on the main screen).
2. Toggle **Tor** on.
3. A bootstrap progress indicator shows Tor connecting. Wait until it shows "Ready."
4. All internet traffic (Nostr relays, location channels) now routes through Tor.

> Tor increases privacy but may increase latency. Bluetooth mesh traffic is not affected by Tor.

---

## 11. Settings

Open settings by tapping the **gear icon** on the main screen.

| Setting | Description |
|---|---|
| **Profile** | Your own avatar, nickname, display name and bio — publish to Nostr with one tap |
| **Theme** | Choose System, Light, Dark or AMOLED (pure black) |
| **Theme pack** | Color pack for the whole app: Bitchat, Matrix, Amber, Cyber, Hacker or Ghost |
| **Chat layout** | Bubbles or compact IRC-style lines |
| **Text size** | Chat font size from 12 to 22 |
| **Time format** | 24-hour or 12-hour clock |
| **Blocked words** | Messages containing these words are hidden |
| **Hide images until tapped** | Privacy mode for pictures received from other people |
| **Auto-reply when away** | Answer mentions automatically while you are away |
| **Ghost mode** | Stay invisible: no presence, no mesh announcements, no typing indicator |
| **Proof of Work (PoW)** | Set the difficulty for outgoing Nostr messages. Higher values slow down spam bots but also slightly slow your own sending. Range: 0 (off) to extreme (very slow). |
| **Tor** | Enable/disable Tor routing for internet connections |
| **Battery Optimization** | Re-open Android battery settings to adjust optimization for Vuzol |
| **Favorites** | View and manage your bookmarked peers and channels |
| **Debug Settings** | Developer tools: mesh topology visualizer, connection diagnostics, raw packet logs |

---

## 12. Emergency Data Deletion

If you need to erase all app data immediately:

1. Navigate to the **main screen**.
2. **Triple-tap the app title** ("Vuzol") at the top of the screen.
3. A confirmation dialog appears warning you this action is irreversible.
4. Confirm to wipe all messages, cryptographic keys, identity, settings, and favorites.

After deletion the app restarts as if freshly installed. This feature is designed for high-risk situations where you need to eliminate evidence of the app's use quickly.

---

## 13. Troubleshooting

### No peers appearing in the list

- Make sure Bluetooth is enabled.
- Make sure Location Services are enabled — Android requires them for BLE scanning.
- Check that battery optimization is disabled for Vuzol (Settings → Apps → Vuzol → Battery → Unrestricted).
- Move closer to other users — typical BLE range is 10–30 metres.
- Restart the app to re-initialize the mesh service.

### Messages not being delivered

- The recipient may not currently be reachable on the mesh. Messages are stored and will be delivered when they reconnect.
- If using Location Channels or Nostr, check your internet connection.
- If Tor is enabled, wait for the bootstrap indicator to show "Ready."

### App stops running in the background

- Re-open the app and go to **Settings → Battery Optimization** and set Vuzol to **Unrestricted** or **No restrictions** in your Android battery settings.
- Some device manufacturers (Samsung, Xiaomi, Huawei, etc.) have aggressive background-kill policies. Look up your device model and "disable background app kill" for additional steps specific to your phone.

### Voice notes not recording

- Make sure the **Microphone** permission is granted. Go to Android Settings → Apps → Vuzol → Permissions → Microphone → Allow.

### Cannot scan QR code for verification

- Make sure the **Camera** permission is granted. Go to Android Settings → Apps → Vuzol → Permissions → Camera → Allow.

### Tor is stuck connecting

- Check your internet connection.
- Some networks block Tor. Try switching between Wi-Fi and mobile data.
- Disable and re-enable Tor in Settings to restart the bootstrap process.

---

## 14. Permissions Reference

| Permission | Why it is needed |
|---|---|
| Bluetooth | Core BLE mesh communication |
| Location (Precise) | Required by Android to scan for BLE devices |
| Location (Background) | Keeps mesh active when the app is in the background |
| Nearby Devices (Android 12+) | BLE advertising and scanning on newer Android |
| Notifications | Alert you to incoming messages |
| Microphone | Record voice notes |
| Camera | Scan QR codes for peer verification |
| Photos / Media | Send images from your gallery |
| Battery Optimization Exemption | Prevent Android from killing the background mesh service |
| Run at Startup | Restart the mesh service automatically after a device reboot |

---

*Vuzol is open-source and community-driven. For bug reports or feature requests, visit the project repository.*
