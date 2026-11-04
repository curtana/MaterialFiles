# Changelog

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
