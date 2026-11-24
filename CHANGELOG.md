# Changelog

## 1.0.0 - 2026-11-24
First public release.

- Calendar menu (`/daily`) with up to 28 days: claimed days, today's glowing reward, upcoming rewards and a streak info item.
- One claim per calendar day in a configurable timezone (`/daily timezone`).
- Streaks with `on-miss` RESET or KEEP, grace days for everyone, LOOP or REPEAT-LAST after the last day.
- Streak bonuses at any milestone, with server-wide broadcasts.
- Streak freezes per player: each missed day uses one automatically (`/daily freezes give|take|set`, console-friendly, optional maximum).
- Join reminder with a clickable [CLAIM] button, or open the calendar on join.
- Admin commands: info, setstreak, setday, full reset, plus nextday/missday test tools.
- Rewards as items and console commands; everything configurable with MiniMessage; new settings are added to existing configs automatically.
