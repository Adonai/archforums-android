package com.kanedias.holywarsoo.dto

import java.io.Serializable

/**
 * @author Kanedias
 *
 * Created on 17.12.19
 */
data class Forum(
    val id: Int,

    // info
    val anchor: NamedLink,
    val subtext: String,
    val lastMessage: NamedLink,
    val lastMessageDate: String,

    // counters
    val themeCount: Int = -1,
    val commentsCount: Int = -1,
    val pageCount: Int = -1,
    val currentPage: Int = -1,

    // child entities
    val subforums: List<Forum> = emptyList(),
    val topics: List<ForumTopic> = emptyList()
) : Serializable