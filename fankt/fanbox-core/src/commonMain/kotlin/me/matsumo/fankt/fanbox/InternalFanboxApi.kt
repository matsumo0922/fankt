package me.matsumo.fankt.fanbox

/**
 * fankt のモジュール間でだけ使う宣言。
 *
 * fanbox-core の宣言のうち、fanbox の host と guest が使うために public にしたものに付ける。利用者向けの API ではなく、
 * 互換性を保証しない。
 */
@RequiresOptIn(
    message = "This declaration is shared between fankt modules and is not part of the public API.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class InternalFanboxApi
