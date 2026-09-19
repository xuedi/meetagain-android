package org.meetagain.app.core.network

import kotlinx.serialization.Serializable

/** `GroupList` in the API description. */
@Serializable
data class GroupListDto(val items: List<GroupSummaryDto>, val total: Int)

/** `GroupSummary` in the API description. [visibility] is `public`, `hidden` or `private`. */
@Serializable
data class GroupSummaryDto(
    val slug: String,
    val name: String,
    val visibility: String? = null,
    val domain: String? = null,
    val logoUrl: String? = null,
    val detailUrl: String
)

/** `GroupDetail` in the API description. [description] is one string, not translated. */
@Serializable
data class GroupDetailDto(
    val slug: String,
    val name: String,
    val visibility: String? = null,
    val domain: String? = null,
    val logoUrl: String? = null,
    val detailUrl: String,
    val description: String? = null,
    val previewImageUrl: String? = null,
    val languages: List<String>,
    val createdAt: String? = null,
    val memberCount: Int
)
