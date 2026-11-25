package pl.ambif.ambify.server.accounts

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.ambif.ambify.shared.AccountPremiumRequest
import pl.ambif.ambify.shared.CommunityThemeUpdate
import pl.ambif.ambify.shared.OwnTheme
import pl.ambif.ambify.shared.ThemeChoice
import pl.ambif.ambify.shared.Themes
import pl.ambif.ambify.shared.WallpaperPatterns
import pl.ambif.ambify.shared.WallpaperUploaded

/** 0.7.7 themes; `?community=1` on own themes and wallpapers = the current community's (Premium społeczności). */
fun Route.themeRoutes(themes: ThemeService) {
    authenticate(AUTH) {
        get(Themes.PATH) { call.respond(themes.state(call.session())) }
        put(Themes.ACCOUNT) {
            call.respond(themes.setAccount(call.session(), call.receive<ThemeChoice>().theme))
        }
        put(Themes.MEMBER) { call.respond(themes.setMember(call.session(), call.receive<ThemeChoice>().theme)) }
        put(Themes.COMMUNITY) {
            val update = call.receive<CommunityThemeUpdate>()
            call.respond(themes.setCommunity(call.session(), update.theme, update.locked))
        }
        put(Themes.OWN) {
            val community = call.request.queryParameters["community"] == "1"
            call.respond(themes.saveOwn(call.session(), call.receive<OwnTheme>(), community))
        }
        delete("${Themes.OWN}/{id}") { call.respond(themes.deleteOwn(call.session(), call.parameters["id"]!!)) }
        put(Themes.WALLPAPERS) {
            val session = call.session()
            val community = call.request.queryParameters["community"] == "1"
            val upload = themes.incoming()
            val size = withContext(Dispatchers.IO) {
                call.receiveChannel().toInputStream().use { input ->
                    upload.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (total <= WallpaperPatterns.MAX_BYTES) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            total += read
                        }
                        total
                    }
                }
            }
            if (size > WallpaperPatterns.MAX_BYTES) {
                upload.delete()
                throw ApiException(HttpStatusCode.PayloadTooLarge, "photo_too_big", "Zdjęcie jest za duże (najwyżej 8 MB).")
            }
            val id = withContext(Dispatchers.IO) { themes.addWallpaper(session, upload, community) }
            call.respond(WallpaperUploaded(id))
        }
        get("${Themes.WALLPAPERS}/{id}") {
            val file = themes.wallpaper(call.session(), call.parameters["id"]!!)
            // A new photo gets a new id, so an address never changes its picture.
            call.response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
            call.respond(LocalFileContent(file, ContentType.Image.JPEG))
        }
        post("${Themes.PATH}/platform/premium") {
            val request = call.receive<AccountPremiumRequest>()
            themes.setAccountPremium(call.session(), request.email, request.premium)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
