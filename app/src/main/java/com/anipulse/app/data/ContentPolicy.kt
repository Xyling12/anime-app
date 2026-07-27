package com.anipulse.app.data

import com.anipulse.app.data.shikimori.ShikiAnime
import com.anipulse.app.data.shikimori.ShikiAnimeDetails

/** Client-side safety net for catalog moderation. The gateway has the same denylist. */
object ContentPolicy {
    private val blockedIds = setOf(
        1535L, 2994L,                 // Death Note
        226L, 376L,                   // Elfen Lied
        34542L,                       // Inuyashiki
        22319L, 27899L, 36511L, 37799L, 30458L, 31297L // Tokyo Ghoul + seasons/specials
    )

    fun allowed(id: Long): Boolean = id !in blockedIds
    fun allowed(anime: ShikiAnime): Boolean = allowed(anime.id)
    fun allowed(anime: ShikiAnimeDetails): Boolean = allowed(anime.id)
}
