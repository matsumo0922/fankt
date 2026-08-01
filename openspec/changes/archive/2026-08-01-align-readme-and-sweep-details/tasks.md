## 1. Notification read state

- [x] 1.1 `FanboxEndpoints.bells` の `skipConvertUnreadNotification` を呼び出し側が決める形にする
- [x] 1.2 `FanboxUserRepository.getBells` に既読化指定を通す
- [x] 1.3 `Fanbox.getBells` に既読化指定を公開し、既定を「既読化しない」にする
- [x] 1.4 既読化指定が query に反映されることを検証するテストを追加する

## 2. Documentation

- [x] 2.1 README の Status 節を対応済み投稿形式・未知 type 保持・Cloudflare challenge 未検知に書き換える
- [x] 2.2 README の License 節に CC BY-NC 4.0 を維持する判断を記録する

## 3. Code and build details

- [x] 3.1 `createFanboxJson` から `prettyPrint` を削除する
- [x] 3.2 `FanboxEndpoints` の load size を `Int` にし、query 生成時に文字列化する
- [x] 3.3 `infra-api` bundle から `kotlin-reflect` を外し、`composeApp` へ直接宣言する
- [x] 3.4 `kotlinxCoroutines` の宣言値を実解決値に合わせ、`ktot-logging` の alias typo を修正する

## 4. Validation

- [x] 4.1 ABI dump を更新し、`getBells` の公開 signature 変化を確認する
- [x] 4.2 Detekt と Android unit test、Kotlin/JS test を実行する
- [x] 4.3 `kotlin-reflect` を外した後に `composeApp` がコンパイルできることを確認する
- [x] 4.4 coroutines の解決 version が変更前後で一致することを確認する
