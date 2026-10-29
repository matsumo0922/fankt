package me.matsumo.fankt.fanbox.response

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.FanboxListItemDecoder
import me.matsumo.fankt.fanbox.FanboxTolerantResult
import me.matsumo.fankt.fanbox.createFanboxJson
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxSearchMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxUserMapper
import me.matsumo.fankt.fanbox.datasource.parser.FanboxMetadataExtractor
import me.matsumo.fankt.fanbox.datasource.parser.FanboxMetadataParser
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.PageCursorInfo
import me.matsumo.fankt.fanbox.domain.PageNumberInfo
import me.matsumo.fankt.fanbox.domain.PageOffsetInfo
import me.matsumo.fankt.fanbox.domain.entity.FanboxBellListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPlanListStrictEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPostItemsEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPostsPaginateEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorSearchListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorTagListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxNewsLettersEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPaidRecordListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostCommentListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostSearchEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxTagListEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxBell
import me.matsumo.fankt.fanbox.domain.model.FanboxComment
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlanDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxMetaData
import me.matsumo.fankt.fanbox.domain.model.FanboxNewsLetter
import me.matsumo.fankt.fanbox.domain.model.FanboxPaidRecord
import me.matsumo.fankt.fanbox.domain.model.FanboxPost
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxTag
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointIds

/** レスポンスの解析対象を識別する操作。 */
internal data class FanboxResponseOperation(val name: String, val endpointId: FanboxEndpointId)

internal val fanboxResponseOperations: List<FanboxResponseOperation> = listOf(
    FanboxResponseOperation("post.listHome", FanboxEndpointIds.postListHome),
    FanboxResponseOperation("post.listSupporting", FanboxEndpointIds.postListSupporting),
    FanboxResponseOperation("post.listCreator", FanboxEndpointIds.postListCreator),
    FanboxResponseOperation("post.paginateCreator", FanboxEndpointIds.postPaginateCreator),
    FanboxResponseOperation("post.info", FanboxEndpointIds.postInfo),
    FanboxResponseOperation("post.getComments", FanboxEndpointIds.postGetComments),
    FanboxResponseOperation("post.listTagged", FanboxEndpointIds.postListTagged),
    FanboxResponseOperation("post.likePost", FanboxEndpointIds.postLikePost),
    FanboxResponseOperation("post.likeComment", FanboxEndpointIds.postLikeComment),
    FanboxResponseOperation("post.addComment", FanboxEndpointIds.postAddComment),
    FanboxResponseOperation("post.deleteComment", FanboxEndpointIds.postDeleteComment),
    FanboxResponseOperation("creator.get", FanboxEndpointIds.creatorGet),
    FanboxResponseOperation("creator.listFollowing", FanboxEndpointIds.creatorListFollowing),
    FanboxResponseOperation("creator.listPixiv", FanboxEndpointIds.creatorListPixiv),
    FanboxResponseOperation("creator.listRecommended", FanboxEndpointIds.creatorListRecommended),
    FanboxResponseOperation("plan.listCreator", FanboxEndpointIds.planListCreator),
    FanboxResponseOperation("legacy/support/creator", FanboxEndpointIds.legacySupportCreator),
    FanboxResponseOperation("tag.getFeatured", FanboxEndpointIds.tagGetFeatured),
    FanboxResponseOperation("follow.create", FanboxEndpointIds.followCreate),
    FanboxResponseOperation("follow.delete", FanboxEndpointIds.followDelete),
    FanboxResponseOperation("creator.search", FanboxEndpointIds.creatorSearch),
    FanboxResponseOperation("tag.search", FanboxEndpointIds.tagSearch),
    FanboxResponseOperation("plan.listSupporting.strict", FanboxEndpointIds.planListSupporting),
    FanboxResponseOperation("plan.listSupporting.tolerant", FanboxEndpointIds.planListSupporting),
    FanboxResponseOperation("payment.listPaid", FanboxEndpointIds.paymentListPaid),
    FanboxResponseOperation("payment.listUnpaid", FanboxEndpointIds.paymentListUnpaid),
    FanboxResponseOperation("newsletter.list", FanboxEndpointIds.newsletterList),
    FanboxResponseOperation("bell.list", FanboxEndpointIds.bellList),
    FanboxResponseOperation("homepage", FanboxEndpointIds.homepage),
)

/** endpoint に対応するレスポンス解析とモデル変換を提供する。 */
// endpoint ごとのレスポンス変換を対応付ける。
@Suppress("TooManyFunctions")
internal object FanboxResponses {
    private val formatter = createFanboxJson()

    fun homePosts(
        body: String,
        statusCode: Int = 200,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxTolerantResult<PageCursorInfo<FanboxPost>> = withMappers(body, statusCode, FanboxEndpointIds.postListHome, diagnosticSink) { mappers ->
        mappers.post.map(decode(body, FanboxPostListEntity.serializer()), "post.listHome")
    }

    fun supportingPosts(
        body: String,
        statusCode: Int = 200,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxTolerantResult<PageCursorInfo<FanboxPost>> = withMappers(
        body,
        statusCode,
        FanboxEndpointIds.postListSupporting,
        diagnosticSink,
    ) { mappers ->
        mappers.post.map(decode(body, FanboxPostListEntity.serializer()), "post.listSupporting")
    }

    fun creatorPosts(
        body: String,
        nextCursor: FanboxCursor?,
        statusCode: Int = 200,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxTolerantResult<PageCursorInfo<FanboxPost>> = withMappers(
        body,
        statusCode,
        FanboxEndpointIds.postListCreator,
        diagnosticSink,
    ) { mappers ->
        mappers.post.map(
            decode(body, FanboxCreatorPostItemsEntity.serializer()),
            nextCursor,
            "post.listCreator",
        )
    }

    fun creatorPostsPagination(
        body: String,
        statusCode: Int = 200,
    ): List<FanboxCursor> = withMappers(body, statusCode, FanboxEndpointIds.postPaginateCreator) { mappers ->
        mappers.post.map(decode(body, FanboxCreatorPostsPaginateEntity.serializer()))
    }

    fun postDetail(
        body: String,
        statusCode: Int = 200,
    ): FanboxPostDetail = withMappers(body, statusCode, FanboxEndpointIds.postInfo) { mappers ->
        mappers.post.map(decode(body, FanboxPostDetailEntity.serializer()))
    }

    fun postComments(
        body: String,
        statusCode: Int = 200,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxTolerantResult<PageOffsetInfo<FanboxComment>> = withMappers(
        body,
        statusCode,
        FanboxEndpointIds.postGetComments,
        diagnosticSink,
    ) { mappers ->
        mappers.post.map(decode(body, FanboxPostCommentListEntity.serializer()), "post.getComments")
    }

    fun taggedPosts(
        body: String,
        statusCode: Int = 200,
    ): PageNumberInfo<FanboxPost> = withMappers(body, statusCode, FanboxEndpointIds.postListTagged) { mappers ->
        mappers.post.map(decode(body, FanboxPostSearchEntity.serializer()))
    }

    // 本文を使わない操作も decoder の (String) -> Unit のシグネチャを維持する。
    @Suppress("UNUSED_PARAMETER")
    fun likePost(body: String): Unit = Unit

    // 本文を使わない操作も decoder の (String) -> Unit のシグネチャを維持する。
    @Suppress("UNUSED_PARAMETER")
    fun likeComment(body: String): Unit = Unit

    // 本文を使わない操作も decoder の (String) -> Unit のシグネチャを維持する。
    @Suppress("UNUSED_PARAMETER")
    fun addComment(body: String): Unit = Unit

    // 本文を使わない操作も decoder の (String) -> Unit のシグネチャを維持する。
    @Suppress("UNUSED_PARAMETER")
    fun deleteComment(body: String): Unit = Unit

    fun creatorDetail(
        body: String,
        statusCode: Int = 200,
    ): FanboxCreatorDetail = withMappers(body, statusCode, FanboxEndpointIds.creatorGet) { mappers ->
        mappers.creator.map(decode(body, FanboxCreatorDetailEntity.serializer()))
    }

    fun followingCreators(
        body: String,
        statusCode: Int = 200,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxTolerantResult<List<FanboxCreatorDetail>> = creatorList(
        body,
        statusCode,
        FanboxEndpointIds.creatorListFollowing,
        diagnosticSink,
    )

    fun followingPixivCreators(
        body: String,
        statusCode: Int = 200,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxTolerantResult<List<FanboxCreatorDetail>> = creatorList(
        body,
        statusCode,
        FanboxEndpointIds.creatorListPixiv,
        diagnosticSink,
    )

    fun recommendedCreators(
        body: String,
        statusCode: Int = 200,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxTolerantResult<List<FanboxCreatorDetail>> = creatorList(
        body,
        statusCode,
        FanboxEndpointIds.creatorListRecommended,
        diagnosticSink,
    )

    fun creatorPlans(
        body: String,
        statusCode: Int = 200,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxTolerantResult<List<FanboxCreatorPlan>> = withMappers(
        body,
        statusCode,
        FanboxEndpointIds.planListCreator,
        diagnosticSink,
    ) { mappers ->
        mappers.creator.map(decode(body, FanboxCreatorPlanListEntity.serializer()), "plan.listCreator")
    }

    fun creatorPlanDetail(
        body: String,
        statusCode: Int = 200,
    ): FanboxCreatorPlanDetail = withMappers(body, statusCode, FanboxEndpointIds.legacySupportCreator) { mappers ->
        mappers.creator.map(decode(body, FanboxCreatorPlanDetailEntity.serializer()))
    }

    fun creatorTags(
        body: String,
        statusCode: Int = 200,
    ): List<FanboxTag> = withMappers(body, statusCode, FanboxEndpointIds.tagGetFeatured) { mappers ->
        mappers.creator.map(decode(body, FanboxCreatorTagListEntity.serializer()))
    }

    // 本文を使わない操作も decoder の (String) -> Unit のシグネチャを維持する。
    @Suppress("UNUSED_PARAMETER")
    fun followCreator(body: String): Unit = Unit

    // 本文を使わない操作も decoder の (String) -> Unit のシグネチャを維持する。
    @Suppress("UNUSED_PARAMETER")
    fun unfollowCreator(body: String): Unit = Unit

    fun searchCreators(
        body: String,
        statusCode: Int = 200,
    ): PageNumberInfo<FanboxCreatorDetail> = withMappers(body, statusCode, FanboxEndpointIds.creatorSearch) { mappers ->
        mappers.search.map(decode(body, FanboxCreatorSearchListEntity.serializer()))
    }

    fun searchTags(
        body: String,
        statusCode: Int = 200,
    ): List<FanboxTag> = withMappers(body, statusCode, FanboxEndpointIds.tagSearch) { mappers ->
        mappers.search.map(decode(body, FanboxTagListEntity.serializer()))
    }

    fun supportingPlansStrict(
        body: String,
        statusCode: Int = 200,
    ): List<FanboxCreatorPlan> = withMappers(body, statusCode, FanboxEndpointIds.planListSupporting) { mappers ->
        mappers.user.map(decode(body, FanboxCreatorPlanListStrictEntity.serializer()))
    }

    fun supportingPlansTolerant(
        body: String,
        statusCode: Int = 200,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxTolerantResult<List<FanboxCreatorPlan>> = withMappers(
        body = body,
        statusCode = statusCode,
        endpointId = FanboxEndpointIds.planListSupporting,
        diagnosticSink = diagnosticSink,
    ) { mappers ->
        mappers.user.map(
            decode(body, FanboxCreatorPlanListEntity.serializer()),
            "plan.listSupporting",
        )
    }

    fun paidRecords(
        body: String,
        statusCode: Int = 200,
    ): List<FanboxPaidRecord> = withMappers(body, statusCode, FanboxEndpointIds.paymentListPaid) { mappers ->
        mappers.user.map(decode(body, FanboxPaidRecordListEntity.serializer()))
    }

    fun unpaidRecords(
        body: String,
        statusCode: Int = 200,
    ): List<FanboxPaidRecord> = withMappers(body, statusCode, FanboxEndpointIds.paymentListUnpaid) { mappers ->
        mappers.user.map(decode(body, FanboxPaidRecordListEntity.serializer()))
    }

    fun newsletters(
        body: String,
        statusCode: Int = 200,
    ): List<FanboxNewsLetter> = withMappers(body, statusCode, FanboxEndpointIds.newsletterList) { mappers ->
        mappers.user.map(decode(body, FanboxNewsLettersEntity.serializer()))
    }

    fun bells(
        body: String,
        statusCode: Int = 200,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxTolerantResult<PageNumberInfo<FanboxBell>> = withMappers(
        body,
        statusCode,
        FanboxEndpointIds.bellList,
        diagnosticSink,
    ) { mappers ->
        mappers.user.map(decode(body, FanboxBellListEntity.serializer()), "bell.list")
    }

    fun homepage(
        body: String,
        extractor: FanboxMetadataExtractor,
        statusCode: Int = 200,
    ): FanboxMetaData = withMappers(body, statusCode, FanboxEndpointIds.homepage) { mappers ->
        mappers.user.map(FanboxMetadataParser(formatter, extractor).parse(body, statusCode, "homepage"))
    }

    private fun creatorList(
        body: String,
        statusCode: Int,
        endpointId: FanboxEndpointId,
        diagnosticSink: FanboxDiagnosticSink,
    ): FanboxTolerantResult<List<FanboxCreatorDetail>> = withMappers(body, statusCode, endpointId, diagnosticSink) { mappers ->
        mappers.creator.map(decode(body, FanboxCreatorListEntity.serializer()), endpointId.value)
    }

    private fun <T> decode(body: String, deserializer: DeserializationStrategy<T>): T = formatter.decodeFromString(deserializer, body)

    private inline fun <T> withMappers(
        body: String,
        statusCode: Int,
        endpointId: FanboxEndpointId,
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
        block: (Mappers) -> T,
    ): T = try {
        block(createMappers(diagnosticSink))
    } catch (failure: FanboxException) {
        throw failure
    } catch (failure: SerializationException) {
        throw FanboxFailureInterpreter.schemaMismatch(statusCode, body, endpointId.value, failure)
    } catch (failure: IllegalArgumentException) {
        throw FanboxFailureInterpreter.schemaMismatch(statusCode, body, endpointId.value, failure)
    }

    private fun createMappers(diagnosticSink: FanboxDiagnosticSink): Mappers {
        val decoder = FanboxListItemDecoder(formatter, diagnosticSink)
        val post = FanboxPostMapper(decoder, formatter)
        val creator = FanboxCreatorMapper(decoder, formatter)
        val user = FanboxUserMapper(post, creator, decoder)
        return Mappers(
            post = post,
            creator = creator,
            search = FanboxSearchMapper(creator),
            user = user,
        )
    }

    /** レスポンス変換に使用する mapper の組。 */
    private data class Mappers(
        val post: FanboxPostMapper,
        val creator: FanboxCreatorMapper,
        val search: FanboxSearchMapper,
        val user: FanboxUserMapper,
    )
}
