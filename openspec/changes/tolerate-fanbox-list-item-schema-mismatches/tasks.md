## 1. OpenSpec と設計

- [x] 1.1 OpenSpec の project files と issue #19 の change を作成する
- [x] 1.2 proposal、delta spec、design を validate し、独立反証ゲートを通す

## 2. Tolerant decode 実装

- [x] 2.1 bounded structural redaction と endpoint/index-path warning を出す共通 item decoder を実装する
- [x] 2.2 対象 response entity の item collection を `JsonElement` に変更する
- [x] 2.3 post、comment、creator、plan、bell mapper を item 単位 decode・map に切り替える
- [x] 2.4 repository から正しい endpoint label を渡し、production mapper に共有 `Json` を配線する
- [x] 2.5 bell mapper の `!!` を全廃し、必須値欠落と unknown type を item mismatch にする
- [x] 2.6 public mismatch event と per-call callback overload を追加し、production decoder へ配線する
- [x] 2.7 callback を caller coroutine context で通知し、`plan.listSupporting` の strict/tolerant Ktorfit methods を分離する

## 3. 検証とドキュメント

- [x] 3.1 timeline と bell の「正常 2 件 + 壊れた 1 件」を production API decode 経由で検証する
- [x] 3.2 nested reply、全対象 endpoint label、diagnostic privacy・redaction・bound、callback correlation/context/exception、supporting-plan strict route、pagination、outer-envelope failure の targeted tests を追加する
- [x] 3.3 README、docs、KDoc の影響を確認し、必要な現在形ドキュメントを更新する
- [ ] 3.4 isolated validation lease で full test・lint・build を実行し、結果と HEAD SHA を記録する
