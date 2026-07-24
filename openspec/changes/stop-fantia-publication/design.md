## Context

`.github/workflows/deploy-library.yml` は release publish と手動実行の両方で FANBOX 関連 artifact と Fantia artifact を Maven Central へ publish する。Fantia は README 上で開発中かつ正式リリース待ちであり、現在のモジュール実装も公開利用を案内できる状態ではない。Maven Central の既存 version は削除できないため、この変更は将来の version の追加を停止する。

## Goals / Non-Goals

**Goals:**

- 次回以降の release workflow で Fantia artifact を publish しない。
- Download 案内を実際に公開している artifact だけに限定し、Fantia が未リリースであることを明示する。
- Fantia の開発用 module を repository に残す。

**Non-Goals:**

- Maven Central に既に公開済みの Fantia artifact を削除しない。
- Fantia module、publishing 設定、source code を削除または再設計しない。
- Fantia のリリース再開条件や自動判定機構を新設しない。

## Decisions

### 1. Fantia 固有の publish step だけを削除する（ユーザー確認済み）

`deploy-library.yml` から `:fankt:fantia:publishAndReleaseToMavenCentral` を実行する step だけを削除し、FANBOX と Room persistence の publish steps は維持する。workflow 全体を条件分岐化する案は、未リリース module が Fantia のみであり、受け入れ条件に不要なため採用しない。

### 2. README の Download 節は公開済み artifact だけを列挙する（ユーザー確認済み）

Fantia の dependency example を削除し、Download 節に Fantia は未リリースである旨を現在形で記載する。Status 節の開発中表示だけに依存すると、Download の code block が利用可能性を誤って示すため、依存追加箇所にも明示する。

### 3. production call path と文書の静的観測で検証する（agent 仮決め）

本変更の本番経路は GitHub release / `workflow_dispatch` から直接実行される `deploy-library.yml` である。Maven Central への実 publish は不可逆な外部操作なので検証では実行せず、workflow に Fantia publish task が存在しないこと、他の publish tasks が残ること、README が Fantia coordinate を案内しないことを repository 上で観測する。

## Risks / Trade-offs

- [Fantia 実装完了後も publish が再開されない] → Fantia の正式リリース変更で workflow step と Download 案内を同じ PR に戻す。
- [既存の空 artifact を利用者が参照できる] → Maven Central から削除できないため既存 version は残るが、README から依存例を除去して新規利用を案内しない。
- [実 publish を伴わないため外部サービス上の挙動を直接確認できない] → production workflow の実行 command 自体を削除し、workflow の構文検証と静的 assertion で対象経路を閉じる。

## Migration Plan

1. Fantia publish step と dependency example を削除する。
2. workflow 構文と Fantia publish command の不在、他 artifact の publish command の残存、README 表示を検証する。
3. 次回 release から新しい Fantia version は publish されない。rollback が必要な場合は削除した step と dependency example を戻す。

## Open Questions

なし。
