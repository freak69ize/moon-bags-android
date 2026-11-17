# Moon Bags

Android home-screen widgets for live crypto prices, market cap, and a read-only portfolio. Open this folder in Android Studio (Ladybug or newer, JDK 17) and let Gradle sync.

## What you get

- Resizable total market cap widget (CoinGecko global cap, responsive Glance sizes)
- 1×1 coin cap or spot tile, 2×1 price pill, sparkline card, scrollable watchlist
- Portfolio net worth widget with an eye that hides balances
- PNG, JPG, and GIF backgrounds. The in-app preview loops GIFs. The launcher uses a downsampled still so RemoteViews stays small.
- Custom splash (1–5 seconds) and a settings background
- Coinbase Advanced Trade JWT (ES256) and Binance HMAC-SHA256, stored with EncryptedSharedPreferences
- Public Ethereum and Solana addresses only. The app never asks for a seed phrase or private key.
- WorkManager refresh at 15, 30, or 60 minutes, plus a refresh action on each widget

## Keys

Create **read-only** keys. On Binance, turn off trading and withdrawals before you paste the key. Coinbase uses a CDP API key name plus its EC private key PEM. Secrets never leave the device.

## Widgets

Long-press the home screen → Widgets → Moon Bags. Each widget opens the configurator before it is placed.
