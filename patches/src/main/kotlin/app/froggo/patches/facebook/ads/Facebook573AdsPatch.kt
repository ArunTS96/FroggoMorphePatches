package app.froggo.patches.facebook.ads

import app.froggo.patches.shared.Constants.COMPATIBILITY_FACEBOOK_573
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue

/*
 * Facebook 573.0.0.37.74 / 473623755 - Feed ads and in-feed recommendations.
 *
 * Proven seams:
 * - MainFeedCSRDataLoaderImpl$maybeDoAsyncAdsTailLoad$1 -> run(): V
 * - MainFeedCSRDataLoaderImpl.maybeDoAsyncAdsTailLoad -> X.1wV.A08(...): V
 * - FeedCSRAdChannelControllerImpl converter -> X.bZU.A00(...): X.3JJ
 * - FeedAsyncAdsController -> X.3JX.A0F(...): X.6Ke
 * - X.1cV.A02(...): route native Home requests to Facebook's Following Feed
 * - X.1vv.addNewEdgeToCollection(...): final sponsored/recommendation edge guard
 * - GraphQLFBMultiAdsFeedUnit.A00(): sponsored-data fallback
 *
 * Reels/video/commercial-break blocking intentionally lives in the separate
 * Block Facebook Reels ads (573) patch. Story Ads have their own patch too.
 */
private fun feedRedexRunnable(originalName: String) = Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    custom = { method, classDef ->
        method.name == "run" && classDef.fields.any { field ->
            field.name == "__redex_internal_original_name" &&
                (field.initialValue as? StringEncodedValue)?.value == originalName
        }
    },
)

private fun feedExactMethod(
    classDescriptor: String,
    methodName: String,
    parameters: List<String> = emptyList(),
) = Fingerprint(
    parameters = parameters,
    custom = { method, classDef ->
        classDef.type == classDescriptor && method.name == methodName
    },
)

private val mainFeedAsyncAdsTailLoadRunnable = feedRedexRunnable(
    "MainFeedCSRDataLoaderImpl\$maybeDoAsyncAdsTailLoad\$1",
)

private val mainFeedAsyncAdsTailLoad = feedExactMethod(
    "LX/1wV;",
    "A08",
    listOf("LX/1wV;"),
)

private val feedAdsResponseConverter = feedExactMethod(
    "LX/bZU;",
    "A00",
    listOf(
        "Lcom/facebook/api/feed/model/FetchFeedParams;",
        "LX/3pN;",
        "LX/41R;",
    ),
)

private val asyncFeedAdsController = feedExactMethod(
    "LX/3JX;",
    "A0F",
    listOf(
        "Lcom/facebook/auth/usersession/FbUserSession;",
        "LX/3pN;",
        "Lcom/facebook/graphql/executor/GraphQLResult;",
    ),
)

private val feedEdgeInsertion = feedExactMethod(
    "LX/1vv;",
    "addNewEdgeToCollection",
    listOf(
        "Lcom/google/common/collect/ImmutableList\$Builder;",
        "Lcom/facebook/graphql/model/GraphQLFeedUnitEdge;",
        "LX/1cP;",
    ),
)

private val nativeNewsFeedParams = feedExactMethod(
    "LX/1cV;",
    "A02",
    listOf(
        "LX/1cP;",
        "Lcom/facebook/api/feedtype/FeedType;",
        "Lcom/facebook/auth/usersession/FbUserSession;",
        "LX/1cV;",
        "LX/5OW;",
        "LX/0y3;",
        "LX/FlR;",
        "Ljava/lang/Boolean;",
        "Ljava/lang/Boolean;",
        "I",
        "I",
    ),
)

private val multiAdsSponsoredData = feedExactMethod(
    "Lcom/facebook/graphql/model/GraphQLFBMultiAdsFeedUnit;",
    "A00",
)

@Suppress("unused")
val blockFacebookFeedAds573Patch = bytecodePatch(
    name = "Block Facebook Feed ads and use Following Feed (573)",
    description = "Blocks ads and makes Home use Facebook’s Following Feed, so it does not request posts from unfollowed accounts.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK_573)

    execute {
        mainFeedAsyncAdsTailLoadRunnable.method.addInstructions(
            0,
            "return-void",
        )
        mainFeedAsyncAdsTailLoad.method.addInstructions(
            0,
            "return-void",
        )
        feedAdsResponseConverter.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """.trimIndent(),
        )
        asyncFeedAdsController.method.addInstructions(
            0,
            """
                new-instance v0, LX/6Ke;
                invoke-static {}, Lcom/google/common/collect/ImmutableList;->of()Lcom/google/common/collect/ImmutableList;
                move-result-object v1
                const/4 v2, 0x0
                invoke-direct {v0, v1, v2}, LX/6Ke;-><init>(Lcom/google/common/collect/ImmutableList;Ljava/lang/String;)V
                return-object v0
            """.trimIndent(),
        )
        nativeNewsFeedParams.method.addInstructions(
            0,
            """
                # native_newsfeed -> following_feed. Other Feed types remain unchanged.
                move-object/from16 v0, p1
                sget-object v1, Lcom/facebook/api/feedtype/FeedType;->A0g:Lcom/facebook/api/feedtype/FeedType;
                if-ne v0, v1, :froggo_following_feed573_keep_type
                sget-object v0, Lcom/facebook/api/feedtype/FeedType;->A0P:Lcom/facebook/api/feedtype/FeedType;
                move-object/from16 p1, v0

                :froggo_following_feed573_keep_type
            """.trimIndent(),
        )
        feedEdgeInsertion.method.addInstructions(
            0,
            """
                move-object/from16 v0, p2
                invoke-virtual {v0}, Lcom/facebook/graphql/model/GraphQLFeedUnitEdge;->B6k()Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;
                move-result-object v1
                sget-object v2, Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;->A0K:Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;
                if-eq v1, v2, :froggo_feedads573_drop_edge
                sget-object v2, Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;->A0I:Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;
                if-eq v1, v2, :froggo_feedads573_drop_edge
                sget-object v2, Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;->A0J:Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;
                if-eq v1, v2, :froggo_feedads573_drop_edge

                # Facebook marks Follow-style recommended stories with this boolean.
                invoke-virtual {v0}, Lcom/facebook/graphql/model/GraphQLFeedUnitEdge;->BO4()LX/3S1;
                move-result-object v1
                instance-of v2, v1, Lcom/facebook/graphql/model/GraphQLStory;
                if-eqz v2, :froggo_feedads573_keep_edge
                check-cast v1, Lcom/facebook/graphql/model/GraphQLStory;
                invoke-virtual {v1}, Lcom/facebook/graphql/model/GraphQLStory;->A0d()LX/41R;
                move-result-object v1
                if-eqz v1, :froggo_feedads573_keep_edge
                const v2, 0x6ca24bc9
                invoke-virtual {v1, v2}, Lcom/facebook/graphql/modelutil/BaseModelWithTree;->getCachedBoolean(I)Z
                move-result v1
                const/4 v2, 0x1
                if-eq v1, v2, :froggo_feedads573_drop_edge
                goto :froggo_feedads573_keep_edge

                :froggo_feedads573_drop_edge
                const/4 v0, 0x0
                return v0

                :froggo_feedads573_keep_edge
            """.trimIndent(),
        )
        multiAdsSponsoredData.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """.trimIndent(),
        )
    }
}
