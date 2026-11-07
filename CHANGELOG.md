# Changelog

## v1.10.0

- The chat list looks like Telegram: the folders all, unread, groups and people, a side menu with the profile, the calls and the settings, and a separate tab with the history of the calls.
- The menu of a message is compact now: a small grid of buttons over the chat, the long press works on the whole message and not only on the time of the message.
- A channel that is opened from a link keeps its own name, the name of the place is not shown instead of the name of the group.
- Channels and private chats where you wrote stay in the chat list, they do not disappear after you leave them.
- Deleted messages do not come back with the next message of the chat.
- The offline room is remembered and shows up in the chat list, you can leave it right there.
- The landing page and the README are updated automatically with every release.

## v1.9.0

- Offline room inside the local WiFi network: the phones of the room find each other by themselves, without router setup and without internet. Text messages and files are encrypted with the room password.
- Files can be sent inside the offline room, the received file is opened with any app of the phone.
- Voice calls inside the offline room: the sound goes over UDP, encrypted with the room key.
- WiFi Direct rooms work on Android 12 and older again, the old platform API is called through reflection.
- Fixed the crash with duplicate attachments and the duplicate key in the message list.

## v1.8.0

- Fast and reliable forwarding of photos, videos and files: media is compressed and sent in small encrypted chunks with acknowledgements and retries of the lost parts only.
- Group history: new members of a geographic group receive the recent chat history instead of an empty conversation.
- Private group passwords: a group can be protected with a password, the password travels inside the invite link and the group content is encrypted.
- Saved messages became a real chat with bubbles, timestamps and photo previews.
- The landing page now speaks Ukrainian or English automatically, depending on the browser language.
- Experimental WiFi Direct room: offline chat without router and internet, the host phone relays the messages so every phone in range hears everybody (needs Android 15 or newer).
- Fixed the crash in the customization section: changing the app icon and name now works and the old icon is retired safely.

## v1.7.2

- Stable fullscreen video player with precise photo zoom.
- Reliable group invites with a web link that works in any browser.
