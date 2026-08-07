package me.matsumo.fankt.fanbox

/**
 * アプリに同梱した guest bundle を読み出す経路。
 *
 * 配信先へ到達できない場合に、この経路から読み出した bundle が guest として実行される。渡さない
 * 場合、配信不達時は guest を経由しない既存経路で処理される。
 *
 * [read] が受け取るのはディレクトリ内のファイル名であり、次の 2 種類が渡る。パスの区切りを含む
 * 値は渡らない。
 *
 * - `fanbox-guest.manifest.zipline.json`: manifest
 * - 各モジュールの SHA-256 を hex で表した名前（拡張子なし）
 *
 * 該当するファイルを持たない場合は例外ではなく `null` を返すこと。呼び出し側は `null` を「同梱
 * bundle が無い」と解釈して既存経路へ退避する。例外を投げた場合も退避するが、その失敗は診断へ
 * 報告される。
 *
 * 同梱するディレクトリの内容は Zipline plugin のビルド出力とは異なる。plugin の出力をそのまま
 * 配置すると manifest の名前が一致せず、静かに既存経路へ退避する。生成手順は README を参照。
 */
fun interface FanboxEmbeddedGuestBundle {
    suspend fun read(fileName: String): ByteArray?
}
