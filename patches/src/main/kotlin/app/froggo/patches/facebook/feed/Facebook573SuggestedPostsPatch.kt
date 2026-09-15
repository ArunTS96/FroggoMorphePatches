package app.froggo.patches.facebook.feed

import app.froggo.patches.shared.Constants.COMPATIBILITY_FACEBOOK_573
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/*
 * Facebook 573.0.0.37.74 / 473623755 - in-feed recommendations only.
 *
 * GraphQLFeedStoryCategory.A0J is SHOWCASE in this exact APK. Facebook uses
 * it on the IN_FEED_RECOMMENDATION path before the unit reaches the final
 * FeedUnitCollectionManager insertion seam. Ordinary Feed units remain
 * ORGANIC, so this removes suggested creators/posts without filtering posts
 * from friends, followed people, or followed groups.
 */
private val suggestedFeedEdgeInsertion = Fingerprint(
    parameters = listOf(
        "Lcom/google/common/collect/ImmutableList$Builder;",
        "Lcom/facebook/graphql/model/GraphQLFeedUnitEdge;",
        "LX/1cP;",
    ),
    custom = { method, classDef ->
        classDef.type == "LX/1vv;" && method.name == "addNewEdgeToCollection"
    },
)

@Suppress("unused")
val hideFacebookSuggestedPosts573Patch = bytecodePatch(
    name = "Hide Facebook suggested posts (573)",
    description = "Removes Facebook's in-feed recommended posts while preserving organic posts from friends and followed sources.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK_573)

    execute {
        suggestedFeedEdgeInsertion.method.addInstructions(
            0,
            """
                move-object/from16 v0, p2
                invoke-virtual {v0}, Lcom/facebook/graphql/model/GraphQLFeedUnitEdge;->B6k()Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;
                move-result-object v1
                sget-object v2, Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;->A0J:Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;
                if-eq v1, v2, :froggo_suggestions573_drop_edge
                goto :froggo_suggestions573_keep_edge

                :froggo_suggestions573_drop_edge
                const/4 v0, 0x0
                return v0

                :froggo_suggestions573_keep_edge
            """.trimIndent(),
        )
    }
}
