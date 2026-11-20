# Changelog

## [v1.7.5-beta.2] - 2026-11-20

### Added
- The replace conflict dialog warns that replacing deletes the existing file first, and that an interrupted copy or move may lose it. The merge dialog has no warning (#54, #68).
- After the system closes the app in the background, each pane comes back at its folder, and the active pane is restored. The current folder's scroll position also comes back (#11, #70).

### Changed
- Copies follow `cp` and network defaults (#55):
  - A copy with either side off this device (SFTP, SMB, FTP, WebDAV, MTP, and document providers that are not local) no longer copies the modification time or permissions. The copy gets the server's defaults. Phone-to-phone copies and all moves are unchanged (#75, #76).
  - Those copies follow symbolic links and copy what they point to, up to 8 nested folder links. A link loop or a too-deep link fails only that item, with skip, skip all and cancel. A failed folder listing can also be retried (#78).
- A folder, archive or Downloads shortcut opened from another app while the file list is on top now opens in the active pane of that window. The other pane stays where it is. A file picker screen opens it in a new window instead (#80, #89).
- Material 3 light theme: secondary text, such as the toolbar subtitle, the path bar and navigation drawer subtitles, is darker and easier to read (#27, #61, #65).

### Fixed
- "Replace" now opens the source (or lists a source folder) before deleting the existing item, on local, root, document provider, SFTP, SMB, FTP and WebDAV storage and between them. If the source cannot be read, the existing item is kept and the copy error is shown. This also covers FTP and WebDAV sources that cannot be copied, such as symbolic links (#55, #56, #66, #71).
- Replacing a symbolic link no longer reports a new conflict after it succeeded. With "Apply to all" this retried the same item forever (#47, #63).
- The new name typed in the conflict dialog, and the "Rename" button, survive rotation and the app being closed in the background (#67, #82).
- The Korean replace message no longer names the folder as if it were the file (#58, #68).
- Relative FTP symbolic links now resolve against the link's folder, so their type and size show correctly (#55, #77).
- Copy progress no longer goes past 100% when links are followed (#55, #78).
- An archive's file list refreshes when the archive file changes. A path that no longer exists in it shows an error instead of the old contents (#69, #84).
- Refreshing a large archive no longer freezes the app while its entries are being read (#83, #88).
- Entering an archive password no longer freezes the app while the archive is being read (#86, #91).

### Known issues
- "Replace" still deletes the existing item before writing the new one. The source is checked first and the dialog warns, but an interrupted copy can still lose the existing file (#54).
- Root and Shizuku copy paths were not run on a device. A stuck root copy can only be cancelled (#43).
- Local and root copies may not carry over extended attributes (unreproduced, found by code reading, #48).
- New strings are English and Korean only. Other locales fall back to English (#21).
- Copies into device storage opened through the system file picker (SAF) get the copy time, not the original modification time. Document providers cannot set it. This is a platform limit (#74).
- On "Replace", a document provider source that cannot be opened as a stream (for example a virtual document) now fails instead of deleting the target and copying (#55).
- FTP cannot tell "not found" from "denied" (reply 550). If the server refuses to delete the existing file, the upload decides: the replace succeeds if the server allows overwriting, and otherwise fails with the existing file kept (#55).
- Copy, replace, attributes and link following were not tested against a real SFTP, SMB, FTP or WebDAV server. SMB was not exercised for the job list either.
- Opening in a new window (the "New window" action, and a file picker screen receiving a path) was checked on Android 16 (API 36) only. On older versions it may be ignored (#80).
- Opening a delivered path in the active pane was not checked on a tablet in two-pane landscape (#80).

### Docs
- Design docs are marked implemented for this release and match the shipped behaviour: Material 3 is the default theme on Android 12 and later (#54), the restored scroll position (#11), restored active pane and delivered paths (#2, #11, #80), the replace and copy-default docs (#55, #74). The standards traceability table covers #11 and #80.

## [v1.7.5-beta.1] - 2026-11-04

### Added
- Dual-pane file list: two panes side by side when the window is at least 600dp wide, one pane at a time on narrower windows. Swipe right to left to switch panes, or tap a pane to make it active. Copy or cut in one pane and paste in the other (#2, #5, #6, #7, #15, #22, #28).
- Confirmation before a copy, move or extract starts (#8, #20).
- A running stick figure in the file list toolbar while a file job runs. It cheers or falls over when the job ends. A setting turns it off (#30, #35).
- "File operations" in the file list overflow menu: one row per job with type, target, progress and cancel, plus "Cancel all" (#36, #46).
- A toast when a file job ends. It also reports a partial file that could not be deleted (#36, #46).

### Changed
- Synced with upstream 1.7.5. The minimum Android version is now 6.0 (API 23) and the target SDK is 36 (#26).
- Automatic file list refreshes are silent when a list is already shown: no loading spinner, and the list stays in place. Pull to refresh and other explicit refreshes still show loading. A failed silent refresh keeps the list, shows "Error" in the toolbar subtitle and shows one toast (#29, #33).
- A pane hidden in single-pane mode does not reload its list until it is shown again (#29, #33).
- With battery saver on, the stick figure shows still poses instead of moving (#38, #41).

### Fixed
- An SFTP connection could stay broken after one failed request. It now reconnects on the next call (#29, #33).
- Opening an SFTP file from another app could fail with a network-on-main-thread error (#29, #33).
- Changes arriving during a list load no longer cancel it, which kept SFTP folders reloading and failing during a transfer (#29, #33).
- A job ending with a fatal error no longer keeps the wake and Wi-Fi locks held (#34, #35).
- The stick figure's animation stops when the view leaves the window (#40, #41).
- Canceled jobs actually stop, including on backends that consume the interrupt (#36, #46).
- A network timeout is reported as a failure instead of looking like a cancel (#39, #46).
- The text editor's save menu comes back after a save is canceled or fails (#44, #46).

### Known issues
- "Replace" still deletes the existing file first, so an interrupted copy can lose it (#42, planned before 1.7.5).
- Root and Shizuku copy paths were not run on a device. A stuck root copy can only be cancelled (#43).
- New strings are English and Korean only. Other locales fall back to English (#21).
- SMB was not exercised for the job list. FTP, WebDAV and SAF had no test server either.
- Unreproduced defects found by code reading: replacing a symbolic link can report a conflict after it succeeded (#47), and local and root copies may not carry over extended attributes (#48).

### Docs
- Design docs now state minSdk 23 and targetSdk 36, and match the shipped list refresh and battery saver behaviour (#25, #29, #38).
