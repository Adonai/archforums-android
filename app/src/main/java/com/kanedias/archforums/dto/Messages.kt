package com.kanedias.archforums.dto

import android.text.Spanned

enum class NavigationScope {
    FORUM,
    TOPIC,
    MESSAGE
}

/**
 * Entity representing forum message.
 * Forum messages form all topics, they can contain rich text, quotes, spoilers, images etc.
 * Usually any logged in user can write message in any topic unless it is closed.
 *
 * @author Kanedias
 *
 * Created on 2019-12-22
 */
data class ForumMessage(
    /**
     * Unique message identifier. Used in quotes and reports.
     * It is set after page containing this message is loaded.
     */
    val id: Int = -1,

    // info

    /**
     * Permalink to this message
     */
    val link: String,

    /**
     * Navigation links. Used only when searching for messages.
     * @see SearchResults
     */
    val navigationLinks: Map<NavigationScope, Pair<String, String>> = mapOf(),

    /**
     * Author of this message. Can be anonymous.
     */
    val author: String,

    /**
     * True if the message belongs to you and you can edit it
     */
    val isEditable: Boolean,

    /**
     * True if the message belongs to you and you can delete it
     */
    val isDeletable: Boolean,

    /**
     * Avatar of the author. Null means author doesn't have one.
     */
    val authorAvatarUrl: String? = null,

    /**
     * Creation date of this message. Represented as string, because of the way
     * forum page shows recent dates. Later dates can be parsed as ISO8601.
     */
    val createdDate: String,

    /**
     * Main content of the forum message. Markdown converted to spanned.
     * As spanned strings are not serializable, the forum message is not serializable as well.
     */
    val content: Spanned,

    // counters

    /**
     * Index of this message within the topic.
     * Unique across the topic only.
     */
    val index: Int = -1
)

typealias NavLinksMap = HashMap<NavigationScope, Pair<String, String>>