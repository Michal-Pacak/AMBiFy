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
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import pl.ambif.ambify.shared.AccountPaths
import pl.ambif.ambify.shared.ApiHeaders
import pl.ambif.ambify.shared.AuthPaths
import pl.ambif.ambify.shared.CreateCommunityRequest
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Invitation
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.PhotoHidden
import pl.ambif.ambify.shared.PhotoRules
import pl.ambif.ambify.shared.PhotosSetting
import pl.ambif.ambify.shared.RegisterRequest
import pl.ambif.ambify.shared.SetupRequest
import pl.ambif.ambify.shared.Tokens

class ProfilePhotosTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var photosDir: File

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val dir = Files.createTempDirectory("ambify-photos").toFile()
        photosDir = dir.resolve("avatars")
        val database = Db.open(dir.resolve("test.db").path)
        application { module(database, Diagnostics(emptyList()), testTokens(), photosDir = photosDir) }
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

    private suspend fun HttpClient.upload(tokens: Tokens, bytes: ByteArray): HttpResponse = put(AccountPaths.MY_PHOTO) {
        bearerAuth(tokens.accessToken)
        contentType(ContentType.Image.JPEG)
        setBody(bytes)
    }

    private fun picture(width: Int = 1200, height: Int = 900): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = java.awt.Color(120, 160, 200)
        g.fillRect(0, 0, width, height)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    private suspend fun HttpClient.michal(): Tokens =
        send("POST", AuthPaths.SETUP, SetupRequest("Pacakowie", "Michał", "michal@pacak.waw.pl", "sekretne1", "S24")).body()

    private suspend fun HttpClient.join(owner: Tokens, email: String, name: String): Tokens {
        val code = send<Unit>("POST", AccountPaths.INVITATIONS, token = owner.accessToken).body<Invitation>().code
        return send("POST", AuthPaths.REGISTER, RegisterRequest(code, name, email, "haslo1234", "Pixel")).body()
    }

    private suspend fun HttpClient.me(tokens: Tokens): Me = send<Unit>("GET", AccountPaths.ME, token = tokens.accessToken).body()

    private suspend fun HttpClient.family(tokens: Tokens): Family = send<Unit>("GET", AccountPaths.FAMILY, token = tokens.accessToken).body()

    @Test
    fun myPhotoIsSquareSharedAndReplaced() = app {
        val michal = client.michal()
        val ania = client.join(michal, "ania@pacak.waw.pl", "Ania")

        val first = client.upload(michal, picture()).body<Me>().photo
        assertNotNull(first)
        val second = client.upload(michal, picture(800, 800)).body<Me>().photo!!

        val small = client.send<Unit>("GET", "$second?size=s", token = ania.accessToken)
        assertEquals(HttpStatusCode.OK, small.status)
        val image = ImageIO.read(ByteArrayInputStream(small.readRawBytes()))
        assertEquals(PhotoRules.SMALL_SIDE, image.width)
        assertEquals(PhotoRules.SMALL_SIDE, image.height)
        assertEquals(HttpStatusCode.NotFound, client.send<Unit>("GET", first, token = ania.accessToken).status)
        assertEquals(1, photosDir.walk().count { it.isFile && it.name.endsWith("-s.jpg") }, "the old photo is gone")
        assertEquals(second, client.family(ania).members.single { it.name == "Michał" }.avatar?.photo)

        val removed = client.send<Unit>("DELETE", AccountPaths.MY_PHOTO, token = michal.accessToken).body<Me>()
        assertNull(removed.photo)
        assertNull(client.family(ania).members.single { it.name == "Michał" }.avatar?.photo)
    }

    @Test
    fun strangersAndBrokenFilesGetNothing() = app {
        val michal = client.michal()
        val path = client.upload(michal, picture()).body<Me>().photo!!
        val stranger: Tokens = client.send("POST", AuthPaths.REGISTER, RegisterRequest(null, "Obcy", "obcy@x.pl", "haslo1234", "Pixel")).body()
        client.send("POST", AccountPaths.COMMUNITIES, CreateCommunityRequest("Inna"), stranger.accessToken)

        assertEquals(HttpStatusCode.NotFound, client.send<Unit>("GET", path, token = stranger.accessToken).status)
        assertEquals(HttpStatusCode.BadRequest, client.upload(michal, "nie zdjęcie".toByteArray()).status)
        assertEquals(HttpStatusCode.PayloadTooLarge, client.upload(michal, ByteArray((PhotoRules.MAX_BYTES + 10).toInt())).status)
        assertEquals(path, client.me(michal).photo, "a refused upload keeps the photo")
    }

    @Test
    fun communitySwitchesPhotosOffAndHidesOnePerson() = app {
        val michal = client.michal()
        val ania = client.join(michal, "ania@pacak.waw.pl", "Ania")
        val aniaPhoto = client.upload(ania, picture()).body<Me>().photo!!
        client.upload(michal, picture())
        val aniaId = client.me(ania).id

        // The lowest role may not switch photos off.
        assertEquals(HttpStatusCode.Forbidden, client.send("PUT", AccountPaths.FAMILY_PHOTOS, PhotosSetting(true), ania.accessToken).status)

        client.send("PUT", "${AccountPaths.MEMBERS}/$aniaId/${AccountPaths.PHOTO_HIDDEN}", PhotoHidden(true), michal.accessToken)
        val hidden = client.family(michal)
        assertNull(hidden.members.single { it.name == "Ania" }.avatar?.photo)
        assertNotNull(hidden.members.single { it.name == "Michał" }.avatar?.photo)
        assertEquals(listOf("Ania" to "Michał"), hidden.hiddenPhotos.map { it.name to it.byName })
        assertTrue(client.me(ania).communities.single().myPhotoHidden)
        assertEquals(HttpStatusCode.NotFound, client.send<Unit>("GET", aniaPhoto, token = michal.accessToken).status)
        assertEquals(HttpStatusCode.OK, client.send<Unit>("GET", aniaPhoto, token = ania.accessToken).status, "always mine")

        client.send("PUT", "${AccountPaths.MEMBERS}/$aniaId/${AccountPaths.PHOTO_HIDDEN}", PhotoHidden(false), michal.accessToken)
        client.send("PUT", AccountPaths.FAMILY_PHOTOS, PhotosSetting(true), michal.accessToken)
        val blocked = client.family(ania)
        assertTrue(blocked.photosBlocked)
        assertTrue(blocked.members.all { it.avatar?.photo == null })
        assertTrue(blocked.hiddenPhotos.isEmpty(), "only for those who may")
        assertNotNull(client.me(ania).photo, "the account keeps its photo")
    }
}
