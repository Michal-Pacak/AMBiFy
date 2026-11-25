package pl.ambif.ambify.server

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import pl.ambif.ambify.shared.AccountPaths
import pl.ambif.ambify.shared.AccountPremiumRequest
import pl.ambif.ambify.shared.ApiHeaders
import pl.ambif.ambify.shared.AuthPaths
import pl.ambif.ambify.shared.BorderStyle
import pl.ambif.ambify.shared.CommunityThemeUpdate
import pl.ambif.ambify.shared.GrantRequest
import pl.ambif.ambify.shared.Invitation
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.OwnTheme
import pl.ambif.ambify.shared.PlatformPaths
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.ThemeChoice
import pl.ambif.ambify.shared.ThemeState
import pl.ambif.ambify.shared.Themes
import pl.ambif.ambify.shared.Tokens
import pl.ambif.ambify.shared.Wallpaper
import pl.ambif.ambify.shared.WallpaperUploaded

class ThemesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var wallpapers: File

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val dir = Files.createTempDirectory("ambify-themes").toFile()
        wallpapers = dir.resolve("wallpapers")
        val database = Db.open(dir.resolve("test.db").path)
        application { module(database, Diagnostics(emptyList()), testTokens(), wallpapersDir = wallpapers) }
        block()
    }

    private suspend inline fun <reified T> HttpClient.send(
        method: String, path: String, body: T? = null, token: String? = null, community: String? = null,
    ): HttpResponse {
        val configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            token?.let { bearerAuth(it) }
            community?.let { header(ApiHeaders.COMMUNITY, it) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(body))
            }
        }
        return when (method) {
            "GET" -> get(path, configure)
            "POST" -> post(path, configure)
            "PUT" -> put(path, configure)
            else -> delete(path, configure)
        }
    }

    private suspend inline fun <reified T> HttpResponse.body(): T = json.decodeFromString(bodyAsText())

    private suspend fun HttpClient.michal(): Tokens =
        send("POST", AuthPaths.SETUP, SetupRequest("Pacakowie", "Michał", "michal@pacak.waw.pl", "sekretne1", "S24")).body()

    private suspend fun HttpClient.join(owner: Tokens, email: String, name: String): Tokens {
        val code = send<Unit>("POST", AccountPaths.INVITATIONS, token = owner.accessToken).body<Invitation>().code
        return send("POST", AuthPaths.REGISTER, RegisterRequest(code, name, email, "haslo1234", "Pixel")).body()
    }

    private suspend fun HttpClient.state(tokens: Tokens): ThemeState = send<Unit>("GET", Themes.PATH, token = tokens.accessToken).body()

    private fun picture(): ByteArray {
        val image = BufferedImage(3000, 1500, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    private val legia = OwnTheme(id = "legia-0001", name = "Legia", dark = true, bgSat = 0, colors = listOf("#F2F2F2", "#00733E", "#D7142A"), style = BorderStyle.STRIPES)

    @Test
    fun basicForEveryonePremiumOnlyWithPremium() = app {
        val michal = client.michal()
        val ania = client.join(michal, "ania@pacak.waw.pl", "Ania")

        assertNull(client.state(ania).accountTheme, "as the community until chosen")
        assertEquals(Themes.PEARL, client.send("PUT", Themes.ACCOUNT, ThemeChoice(Themes.PEARL), ania.accessToken).body<ThemeState>().accountTheme)
        assertEquals(HttpStatusCode.Forbidden, client.send("PUT", Themes.ACCOUNT, ThemeChoice(Themes.CODE), ania.accessToken).status)
        assertEquals(HttpStatusCode.PaymentRequired, client.send("PUT", Themes.OWN, legia, ania.accessToken).status)

        // Premium konta from the Platform Admin (Michał, the first account).
        assertEquals(HttpStatusCode.Forbidden, client.send("POST", "${Themes.PATH}/platform/premium", AccountPremiumRequest("ania@pacak.waw.pl", true), ania.accessToken).status)
        client.send("POST", "${Themes.PATH}/platform/premium", AccountPremiumRequest("Ania@Pacak.waw.pl", true), michal.accessToken)
        val code = client.send("PUT", Themes.ACCOUNT, ThemeChoice(Themes.CODE), ania.accessToken).body<ThemeState>()
        assertEquals(Themes.CODE, Themes.effective(code, code.communities.single().communityId), "my theme shows in the community too")
        val saved = client.send("PUT", Themes.OWN, legia, ania.accessToken).body<ThemeState>()
        assertEquals(listOf("Legia"), saved.own.map { it.name })
        val mine = client.send("PUT", Themes.MEMBER, ThemeChoice(Themes.custom("legia-0001")), ania.accessToken).body<ThemeState>()
        assertEquals(Themes.custom("legia-0001"), Themes.effective(mine, mine.communities.single().communityId))

        // Premium gone: back to the community's theme, the own theme stays saved.
        client.send("POST", "${Themes.PATH}/platform/premium", AccountPremiumRequest("ania@pacak.waw.pl", false), michal.accessToken)
        val after = client.state(ania)
        assertEquals(Themes.LUXURY, Themes.effective(after, after.communities.single().communityId))
        assertEquals(Themes.LUXURY, Themes.effective(after, null))
        assertEquals(1, after.own.size)
    }

    @Test
    fun ownerSetsAndLocksTheCommunityTheme() = app {
        val michal = client.michal()
        val ania = client.join(michal, "ania@pacak.waw.pl", "Ania")
        client.send("PUT", Themes.MEMBER, ThemeChoice(Themes.NEON), ania.accessToken)

        assertEquals(HttpStatusCode.Forbidden, client.send("PUT", Themes.COMMUNITY, CommunityThemeUpdate(Themes.PEARL, true), ania.accessToken).status)
        assertEquals(HttpStatusCode.Forbidden, client.send("PUT", Themes.COMMUNITY, CommunityThemeUpdate(Themes.GAMING, false), michal.accessToken).status, "Premium needs the grant")
        client.send("PUT", Themes.COMMUNITY, CommunityThemeUpdate(Themes.PEARL, false), michal.accessToken)
        val open = client.state(ania)
        val id = open.communities.single().communityId
        assertEquals(Themes.NEON, Themes.effective(open, id), "not locked: Ania's own pick")

        client.send("PUT", Themes.COMMUNITY, CommunityThemeUpdate(Themes.PEARL, true), michal.accessToken)
        val locked = client.state(ania)
        assertEquals(Themes.PEARL, Themes.effective(locked, id))
        assertEquals("Michał", locked.communities.single().setBy)
        assertEquals(Themes.NEON, locked.communities.single().myChoice, "her pick comes back after unlocking")

        // Premium społeczności: Gaming for the community, and members may pick Kod there without Premium konta.
        client.send("PUT", "${PlatformPaths.COMMUNITIES}/$id/grants/${Themes.COMMUNITY_GRANT}", GrantRequest(true), michal.accessToken)
        client.send("PUT", Themes.COMMUNITY, CommunityThemeUpdate(Themes.GAMING, false), michal.accessToken)
        val chosen = client.send("PUT", Themes.MEMBER, ThemeChoice(Themes.CODE), ania.accessToken).body<ThemeState>()
        assertEquals(Themes.CODE, Themes.effective(chosen, id))
        assertEquals(HttpStatusCode.Forbidden, client.send("PUT", Themes.ACCOUNT, ThemeChoice(Themes.CODE), ania.accessToken).status, "only in that community")
    }

    @Test
    fun communityOwnThemeWithWallpaper() = app {
        val michal = client.michal()
        val ania = client.join(michal, "ania@pacak.waw.pl", "Ania")
        val id = client.state(michal).communities.single().communityId
        client.send("PUT", "${PlatformPaths.COMMUNITIES}/$id/grants/${Themes.COMMUNITY_GRANT}", GrantRequest(true), michal.accessToken)

        val photo = client.put("${Themes.WALLPAPERS}?community=1") {
            bearerAuth(michal.accessToken)
            contentType(ContentType.Image.JPEG)
            setBody(picture())
        }.body<WallpaperUploaded>().photoId
        val stored = ImageIO.read(wallpapers.resolve("$photo.jpg"))
        assertEquals(2048, stored.width, "kept no longer than 2048 px")

        val theme = legia.copy(id = "pacakowie-01", name = "Pacakowie", wallpaper = Wallpaper(photoId = photo, dim = 99))
        val saved = client.send("PUT", "${Themes.OWN}?community=1", theme, michal.accessToken).body<ThemeState>()
        assertEquals(95, saved.communities.single().own.single().wallpaper?.dim, "dim kept readable")
        client.send("PUT", Themes.COMMUNITY, CommunityThemeUpdate(Themes.custom("pacakowie-01"), true), michal.accessToken)

        val seen = client.state(ania)
        assertEquals(Themes.custom("pacakowie-01"), Themes.effective(seen, id))
        assertEquals(HttpStatusCode.OK, client.send<Unit>("GET", "${Themes.WALLPAPERS}/$photo", token = ania.accessToken).status)
        val stranger: Tokens = client.send("POST", AuthPaths.REGISTER, RegisterRequest(null, "Obcy", "obcy@x.pl", "haslo1234", "Pixel")).body()
        assertEquals(HttpStatusCode.NotFound, client.send<Unit>("GET", "${Themes.WALLPAPERS}/$photo", token = stranger.accessToken).status)
        assertEquals(HttpStatusCode.Forbidden, client.send("PUT", "${Themes.OWN}?community=1", theme, ania.accessToken).status)

        val deleted = client.send<Unit>("DELETE", "${Themes.OWN}/pacakowie-01", token = michal.accessToken).body<ThemeState>()
        assertTrue(deleted.communities.single().own.isEmpty())
        assertEquals(Themes.LUXURY, Themes.effective(deleted, id), "a deleted theme falls back")
        assertTrue(!wallpapers.resolve("$photo.jpg").exists(), "its wallpaper goes too")
        assertNull(client.send<Unit>("GET", AccountPaths.ME, token = ania.accessToken).body<Me>().photo)
    }
}
