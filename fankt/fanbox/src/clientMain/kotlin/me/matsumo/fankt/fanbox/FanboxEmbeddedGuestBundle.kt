package me.matsumo.fankt.fanbox

/**
 * アプリに同梱した guest bundle を読み出す経路。配信先へ到達できない場合、ここから読み出した
 * bundle が guest として実行される。
 *
 * [read] が受け取るのは同梱ディレクトリ直下のファイル名で、次の 2 種類に限る。パスの区切りは
 * 含まない。
 *
 * - `fanbox-guest.manifest.zipline.json`: manifest
 * - 各モジュールの SHA-256 を hex で表した名前（拡張子なし）
 *
 * 該当するファイルを持たない場合は例外ではなく `null` を返す。`null` は「同梱 bundle が無い」と
 * 解釈され、guest を経由しない既存経路へ退避する。例外も退避を招くが、失敗として診断へ報告される。
 *
 * 同梱するディレクトリの内容は Zipline plugin のビルド出力と一致しない。ビルド出力をそのまま
 * 配置すると manifest の名前が合わず、退避が無言で起きる。生成手順は README を参照。
 */
fun interface FanboxEmbeddedGuestBundle {
    suspend fun read(fileName: String): ByteArray?
}
