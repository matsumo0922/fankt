## Why

Fantia モジュールは未リリースの開発中機能であるにもかかわらず、リリース workflow が空の artifact を Maven Central へ publish し、README も依存追加例を案内している。次回以降のリリースで未提供 artifact を増やさず、利用者が誤って依存しない状態にする。

## What Changes

- リリース workflow から Fantia モジュールの Maven Central publish step を削除する。
- README の Download 節から `me.matsumo.fankt:fantia` の依存例を削除し、Fantia が未リリースであることを明記する。
- Fantia モジュール自体は開発継続のため残す。

## Capabilities

### New Capabilities

- `fantia-publication-status`: Fantia がリリース対象になるまで publish workflow と利用者向け Download 案内から除外される契約。

### Modified Capabilities

なし。

## Impact

- CI/CD: `.github/workflows/deploy-library.yml` の Maven Central publish steps。
- ドキュメント: `README.md` の Status と Download。
- Maven Central に既に公開済みの Fantia artifact は変更せず、今後の version の追加だけを停止する。
