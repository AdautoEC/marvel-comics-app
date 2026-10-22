package com.adauto.marvel.core.database

import com.adauto.marvel.core.database.entity.ComicEntity
import com.adauto.marvel.core.database.entity.RemoteKeyEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ComicEntityUnitTest {

    @Test
    fun comicEntity_creationAndPropertyAccess() {
        val entity = ComicEntity(
            id = 100,
            position = 5,
            title = "Avengers #5",
            issueNumber = 5.0,
            description = "Earth's Mightiest Heroes",
            thumbnailPath = "http://marvel.com/avengers",
            thumbnailExt = "jpg",
            pageCount = 36,
            priceCents = 499,
            onsaleDate = "2026-05-01",
            creatorsJson = "[\"Stan Lee\", \"Jack Kirby\"]",
            charactersJson = "[\"Iron Man\", \"Thor\", \"Hulk\"]",
            fetchedAt = 123456789L
        )

        assertThat(entity.id).isEqualTo(100)
        assertThat(entity.position).isEqualTo(5)
        assertThat(entity.title).isEqualTo("Avengers #5")
        assertThat(entity.issueNumber).isEqualTo(5.0)
        assertThat(entity.description).isEqualTo("Earth's Mightiest Heroes")
        assertThat(entity.thumbnailPath).isEqualTo("http://marvel.com/avengers")
        assertThat(entity.thumbnailExt).isEqualTo("jpg")
        assertThat(entity.pageCount).isEqualTo(36)
        assertThat(entity.priceCents).isEqualTo(499)
        assertThat(entity.onsaleDate).isEqualTo("2026-05-01")
        assertThat(entity.creatorsJson).isEqualTo("[\"Stan Lee\", \"Jack Kirby\"]")
        assertThat(entity.charactersJson).isEqualTo("[\"Iron Man\", \"Thor\", \"Hulk\"]")
        assertThat(entity.fetchedAt).isEqualTo(123456789L)
    }

    @Test
    fun comicEntity_allowsNullableFields() {
        val entity = ComicEntity(
            id = 200,
            position = 0,
            title = "Secret Wars",
            issueNumber = 1.0,
            description = null,
            thumbnailPath = null,
            thumbnailExt = null,
            pageCount = 48,
            priceCents = null,
            onsaleDate = null,
            creatorsJson = null,
            charactersJson = null,
            fetchedAt = 999999L
        )

        assertThat(entity.description).isNull()
        assertThat(entity.thumbnailPath).isNull()
        assertThat(entity.thumbnailExt).isNull()
        assertThat(entity.priceCents).isNull()
        assertThat(entity.onsaleDate).isNull()
        assertThat(entity.creatorsJson).isNull()
        assertThat(entity.charactersJson).isNull()
    }

    @Test
    fun remoteKeyEntity_creationAndEquality() {
        val key1 = RemoteKeyEntity(comicId = 101, nextOffset = 20)
        val key2 = RemoteKeyEntity(comicId = 101, nextOffset = 20)
        val key3 = RemoteKeyEntity(comicId = 101, nextOffset = null)

        assertThat(key1).isEqualTo(key2)
        assertThat(key1).isNotEqualTo(key3)
        assertThat(key3.nextOffset).isNull()
    }
}
