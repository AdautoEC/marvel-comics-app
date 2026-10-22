package com.adauto.marvel.core.data.mapper

import com.adauto.marvel.core.database.entity.ComicEntity
import com.adauto.marvel.core.network.model.ComicDateDto
import com.adauto.marvel.core.network.model.ComicDto
import com.adauto.marvel.core.network.model.ComicItemSummaryDto
import com.adauto.marvel.core.network.model.ComicPriceDto
import com.adauto.marvel.core.network.model.ComicResourceListDto
import com.adauto.marvel.core.network.model.ThumbnailDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ComicMappersTest {

    @Test
    fun `formatIssueNumber formats integers and decimals properly`() {
        assertThat(formatIssueNumber(1.0)).isEqualTo("#1")
        assertThat(formatIssueNumber(100.0)).isEqualTo("#100")
        assertThat(formatIssueNumber(1.5)).isEqualTo("#1.5")
        assertThat(formatIssueNumber(0.0)).isEqualTo("")
        assertThat(formatIssueNumber(-1.0)).isEqualTo("")
    }

    @Test
    fun `ComicEntity toSummary maps fields correctly`() {
        val entity = ComicEntity(
            id = 1234,
            position = 0,
            title = "Spider-Man",
            issueNumber = 5.0,
            description = "Some description",
            thumbnailPath = "http://i.annihil.us/u/prod/marvel/i/mg/c/e0/535fecbbb9784",
            thumbnailExt = "jpg",
            pageCount = 32,
            priceCents = 399,
            onsaleDate = "2026-01-01",
            creatorsJson = "Stan Lee, Steve Ditko",
            charactersJson = "Peter Parker",
            fetchedAt = 1000L
        )

        val summary = entity.toSummary()

        assertThat(summary.id).isEqualTo(1234)
        assertThat(summary.title).isEqualTo("Spider-Man")
        assertThat(summary.issueNumber).isEqualTo("#5")
        assertThat(summary.coverUrl).isEqualTo("https://i.annihil.us/u/prod/marvel/i/mg/c/e0/535fecbbb9784/portrait_uncanny.jpg")
    }

    @Test
    fun `ComicEntity toSummary handles missing thumbnail`() {
        val entity = ComicEntity(
            id = 1234,
            position = 0,
            title = "Spider-Man",
            issueNumber = 0.0,
            description = null,
            thumbnailPath = null,
            thumbnailExt = null,
            pageCount = 32,
            priceCents = null,
            onsaleDate = null,
            creatorsJson = null,
            charactersJson = null,
            fetchedAt = 1000L
        )

        val summary = entity.toSummary()

        assertThat(summary.id).isEqualTo(1234)
        assertThat(summary.issueNumber).isEqualTo("")
        assertThat(summary.coverUrl).isNull()
    }

    @Test
    fun `ComicEntity toDetail maps and sanitizes fields correctly`() {
        val entity = ComicEntity(
            id = 5678,
            position = 1,
            title = "Avengers",
            issueNumber = 12.0,
            description = "<p>Earth&#39;s &amp; Mightiest Heroes</p>",
            thumbnailPath = "https://i.annihil.us/u/prod/marvel/i/mg/c/e0/avengers",
            thumbnailExt = "png",
            pageCount = 48,
            priceCents = 499,
            onsaleDate = "2026-05-15",
            creatorsJson = "Stan Lee, Jack Kirby",
            charactersJson = "Iron Man, Thor",
            fetchedAt = 2000L
        )

        val detail = entity.toDetail()

        assertThat(detail.id).isEqualTo(5678)
        assertThat(detail.title).isEqualTo("Avengers")
        assertThat(detail.issueNumber).isEqualTo("#12")
        assertThat(detail.description).isEqualTo("Earth's & Mightiest Heroes")
        assertThat(detail.pageCount).isEqualTo(48)
        assertThat(detail.printPrice).isEqualTo("$4.99")
        assertThat(detail.onsaleDate).isEqualTo("2026-05-15")
        assertThat(detail.coverUrl).isEqualTo("https://i.annihil.us/u/prod/marvel/i/mg/c/e0/avengers/portrait_uncanny.png")
        assertThat(detail.creators).containsExactly("Stan Lee", "Jack Kirby").inOrder()
    }

    @Test
    fun `ComicDto toDetail maps and formats price correctly`() {
        val dto = ComicDto(
            id = 9999,
            title = "Iron Man",
            issueNumber = 1.0,
            description = "<b>Invincible</b>",
            pageCount = 36,
            thumbnail = ThumbnailDto(
                path = "http://i.annihil.us/u/prod/marvel/i/mg/c/e0/ironman",
                extension = "jpg"
            ),
            dates = listOf(
                ComicDateDto(type = "focDate", date = "2026-02-01"),
                ComicDateDto(type = "onsaleDate", date = "2026-03-01")
            ),
            prices = listOf(
                ComicPriceDto(type = "digitalPurchasePrice", price = 1.99f),
                ComicPriceDto(type = "printPrice", price = 3.99f)
            ),
            creators = ComicResourceListDto(
                available = 2,
                items = listOf(
                    ComicItemSummaryDto(name = "Larry Lieber", role = "writer"),
                    ComicItemSummaryDto(name = "Don Heck", role = "artist")
                )
            )
        )

        val detail = dto.toDetail()

        assertThat(detail.id).isEqualTo(9999)
        assertThat(detail.title).isEqualTo("Iron Man")
        assertThat(detail.issueNumber).isEqualTo("#1")
        assertThat(detail.description).isEqualTo("Invincible")
        assertThat(detail.pageCount).isEqualTo(36)
        assertThat(detail.printPrice).isEqualTo("$3.99")
        assertThat(detail.onsaleDate).isEqualTo("2026-03-01")
        assertThat(detail.coverUrl).isEqualTo("https://i.annihil.us/u/prod/marvel/i/mg/c/e0/ironman/portrait_uncanny.jpg")
        assertThat(detail.creators).containsExactly("Larry Lieber", "Don Heck").inOrder()
    }

    @Test
    fun `ComicDto toEntity maps fields with position and fetchedAt timestamp`() {
        val dto = ComicDto(
            id = 777,
            title = "Thor",
            issueNumber = 3.0,
            description = "God of Thunder",
            pageCount = 28,
            thumbnail = ThumbnailDto(path = "http://example.com/thor", extension = "jpg"),
            dates = listOf(ComicDateDto(type = "onsaleDate", date = "2026-04-01")),
            prices = listOf(ComicPriceDto(type = "printPrice", price = 4.99f)),
            creators = ComicResourceListDto(
                available = 1,
                items = listOf(ComicItemSummaryDto(name = "Walt Simonson"))
            ),
            characters = ComicResourceListDto(
                available = 1,
                items = listOf(ComicItemSummaryDto(name = "Thor Odinson"))
            )
        )

        val entity = dto.toEntity(position = 5, fetchedAt = 12345678L)

        assertThat(entity.id).isEqualTo(777)
        assertThat(entity.position).isEqualTo(5)
        assertThat(entity.title).isEqualTo("Thor")
        assertThat(entity.issueNumber).isEqualTo(3.0)
        assertThat(entity.description).isEqualTo("God of Thunder")
        assertThat(entity.thumbnailPath).isEqualTo("http://example.com/thor")
        assertThat(entity.thumbnailExt).isEqualTo("jpg")
        assertThat(entity.pageCount).isEqualTo(28)
        assertThat(entity.priceCents).isEqualTo(499)
        assertThat(entity.onsaleDate).isEqualTo("2026-04-01")
        assertThat(entity.creatorsJson).isEqualTo("Walt Simonson")
        assertThat(entity.charactersJson).isEqualTo("Thor Odinson")
        assertThat(entity.fetchedAt).isEqualTo(12345678L)
    }
}
