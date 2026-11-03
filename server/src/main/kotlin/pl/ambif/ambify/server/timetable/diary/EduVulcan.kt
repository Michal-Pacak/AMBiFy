package pl.ambif.ambify.server.timetable.diary

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import pl.ambif.ambify.shared.ChangeKind
import pl.ambif.ambify.shared.LessonChange

/** The registered "device" of one parent account: its RSA key and the units (tenants) it was registered in. */
@Serializable
internal data class VulcanCredential(
    val restUrls: List<String>,
    /** Base64 DER of the public key (SubjectPublicKeyInfo). */
    val certificate: String,
    /** Base64 DER of the private key: PKCS#8 (ours) or PKCS#1 (made by the Python iris library). */
    val privateKey: String,
    val fingerprint: String,
    val deviceId: String,
    val deviceOs: String = "Android",
    val deviceModel: String = "AMBiFy",
) {
    fun key(): PrivateKey {
        val der = Base64.getDecoder().decode(privateKey)
        val pkcs8 = runCatching { KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der)) }.getOrNull()
        return pkcs8 ?: KeyFactory.getInstance("RSA").generatePrivate(
            PKCS8EncodedKeySpec(PrivateKeyInfo(AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE), ASN1Primitive.fromByteArray(der)).encoded),
        )
    }

    companion object {
        fun create(): VulcanCredential {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val certificate = Base64.getEncoder().encodeToString(pair.public.encoded)
            return VulcanCredential(
                restUrls = emptyList(),
                certificate = certificate,
                privateKey = Base64.getEncoder().encodeToString(pair.private.encoded),
                fingerprint = VulcanSigner.fingerprint(certificate),
                deviceId = UUID.randomUUID().toString(),
            )
        }
    }
}

/**
 * How the eduVulcan mobile API (Hebe CE) signs requests, as in the iris library (bbrjpl1310b/iris, the successor
 * of vulcan-api): a SHA256withRSA signature of the canonical URL, the body digest and the date.
 */
internal object VulcanSigner {
    private val dateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)

    /** MD5 (hex) of the public key in PEM, exactly as Python's `cryptography` writes it. */
    fun fingerprint(certificate: String): String {
        val pem = "-----BEGIN PUBLIC KEY-----\n" + certificate.chunked(64).joinToString("\n") + "\n-----END PUBLIC KEY-----\n"
        return MessageDigest.getInstance("MD5").digest(pem.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** The part of the URL from "api/mobile/" on, percent-encoded (also "/"), lower case. */
    fun canonicalUrl(url: String): String {
        val path = Regex("api/mobile/.*").find(url)?.value ?: url
        return buildString {
            for (byte in path.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt().toChar()
                if (c.isLetterOrDigit() && c.code < 128 || c in "_.-~") append(c) else append("%%%02X".format(byte.toInt() and 0xff))
            }
        }.lowercase()
    }

    fun headers(url: String, body: String?, pupilId: Long?, credential: VulcanCredential, now: ZonedDateTime): Map<String, String> {
        val headers = linkedMapOf(
            "vOS" to credential.deviceOs,
            "vVersionCode" to EduVulcan.APP_VERSION_CODE,
            "vAPI" to "1",
            "vCanonicalUrl" to canonicalUrl(url),
            "vDate" to now.withZoneSameInstant(ZoneOffset.UTC).format(dateFormat),
            "User-Agent" to "Dart/3.8 (dart:io)",
        )
        if (pupilId != null) headers["vHint"] = pupilId.toString()
        headers["Content-Type"] = if (body != null) "application/json; charset=utf-8" else "application/json"
        val digest = body?.let { Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8))) }
        val signed = if (digest != null) listOf("vCanonicalUrl" to headers["vCanonicalUrl"]!!, "Digest" to digest, "vDate" to headers["vDate"]!!)
        else listOf("vCanonicalUrl" to headers["vCanonicalUrl"]!!, "vDate" to headers["vDate"]!!)
        headers["Signature"] = signature(signed, credential)
        if (digest != null) headers["Digest"] = "SHA-256=$digest"
        return headers.mapKeys { it.key.lowercase() }
    }

    fun signature(signed: List<Pair<String, String>>, credential: VulcanCredential): String {
        val rsa = Signature.getInstance("SHA256withRSA").apply {
            initSign(credential.key())
            update(signed.joinToString("") { it.second }.toByteArray(Charsets.UTF_8))
        }
        val value = Base64.getEncoder().encodeToString(rsa.sign())
        return "keyId=\"${credential.fingerprint}\",headers=\"${signed.joinToString(" ") { it.first }}\"," +
            "algorithm=\"sha256withrsa\",signature=Base64(SHA256withRSA($value))"
    }
}

/**
 * eduVulcan: the parent signs in at eduvulcan.pl, the app reads the one-time tokens from https://eduvulcan.pl/api/ap
 * and the server registers its own "device" with them; afterwards the server reads the timetable with that key.
 * This is the unofficial mobile API (as used by the iris library); Vulcan may change it, then the update fails and
 * the error is shown in the timetable settings.
 */
class EduVulcan(private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()) : SchoolDiary {
    private val json = Json { ignoreUnknownKeys = true }

    override fun connect(grant: List<String>): DiaryLink {
        if (grant.isEmpty()) throw DiaryException("eduVulcan nie pokazał żadnego dziecka na tym koncie. Zaloguj się kontem rodzica.")
        var credential = VulcanCredential.create()
        val byTenant = grant.groupBy { tenantOf(it) }
        for ((tenant, tokens) in byTenant) {
            val restUrl = "$API_BASE_URL/$tenant/api"
            request("POST", "$restUrl/mobile/register/jwt", credential = credential, envelope = buildJsonObject {
                put("OS", credential.deviceOs)
                put("Certificate", credential.certificate)
                put("CertificateType", "RSA_PEM")
                put("DeviceModel", credential.deviceModel)
                put("SelfIdentifier", credential.deviceId)
                put("CertificateThumbprint", credential.fingerprint)
                put("Tokens", buildJsonArray { tokens.forEach { add(JsonPrimitive(it)) } })
            })
            credential = credential.copy(restUrls = credential.restUrls + restUrl)
        }
        val stored = json.encodeToString(VulcanCredential.serializer(), credential)
        return DiaryLink(stored, pupils(stored))
    }

    override fun pupils(credential: String): List<DiaryPupilData> {
        val vulcan = json.decodeFromString(VulcanCredential.serializer(), credential)
        return vulcan.restUrls.flatMap { restUrl ->
            request("GET", "$restUrl/mobile/register/hebe", mapOf("mode" to "2"), credential = vulcan).jsonArray.map { it.jsonObject }
        }.filter { account -> account["Periods"].array().any { it.jsonObject["Current"]?.jsonPrimitive?.booleanOrNull == true } }
            .map { account ->
                val pupil = account["Pupil"]!!.jsonObject
                val unit = account["Unit"]!!.jsonObject
                DiaryPupilData(
                    key = "${unit.text("RestURL")}|${pupil["Id"]!!.jsonPrimitive.content}",
                    firstName = pupil.text("FirstName"),
                    surname = pupil.text("Surname"),
                    clazz = account.text("ClassDisplay"),
                    // The fuller of the two names ("Szkoła Podstawowa nr 123 im. …" rather than "Szkoła Podstawowa").
                    school = listOf(unit.text("DisplayName"), account["ConstituentUnit"].obj()?.text("Name").orEmpty()).maxBy { it.length },
                )
            }.distinctBy { it.key }
    }

    override fun lessons(credential: String, pupilKey: String, from: LocalDate, to: LocalDate): List<DiaryLesson> {
        val vulcan = json.decodeFromString(VulcanCredential.serializer(), credential)
        val restUrl = pupilKey.substringBeforeLast('|')
        val pupilId = pupilKey.substringAfterLast('|').toLong()
        val query = mapOf(
            "pupilId" to pupilId.toString(),
            "dateFrom" to from.toString(),
            "dateTo" to to.toString(),
            "lastId" to Int.MIN_VALUE.toString(),
            "pageSize" to "500",
            "lastSyncDate" to "1970-01-01 00:00:00",
        )
        return parseSchedule(request("GET", "$restUrl/mobile/schedule/withchanges/byPupil", query, pupilId, vulcan).jsonArray)
    }

    override fun disconnect(credential: String) {
        val vulcan = json.decodeFromString(VulcanCredential.serializer(), credential)
        vulcan.restUrls.forEach { runCatching { request("DELETE", "$it/mobile/register", credential = vulcan) } }
    }

    private fun request(
        method: String,
        url: String,
        query: Map<String, String> = emptyMap(),
        pupilId: Long? = null,
        credential: VulcanCredential,
        envelope: JsonElement? = null,
    ): JsonElement {
        val body = envelope?.let { body(it) }
        val uri = if (query.isEmpty()) url else url + "?" + query.entries.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        val builder = HttpRequest.newBuilder(URI(uri)).timeout(Duration.ofSeconds(30))
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it, Charsets.UTF_8) } ?: HttpRequest.BodyPublishers.noBody())
        VulcanSigner.headers(url, body, pupilId, credential, ZonedDateTime.now()).forEach { (name, value) -> builder.header(name, value) }
        val response = try {
            http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw DiaryException("Brak połączenia z eduVulcan. Spróbuję ponownie przy następnej aktualizacji.", e)
        }
        val text = response.body()
        if (response.statusCode() != 200 || "!DOCTYPE" in text) {
            throw DiaryException("eduVulcan odpowiedział błędem (${response.statusCode()}). Spróbuję ponownie przy następnej aktualizacji.")
        }
        val answer = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw DiaryException("eduVulcan odpowiedział w nieznany sposób.")
        val status = answer["Status"].obj()
        when (val code = status?.get("Code")?.jsonPrimitive?.intOrNull ?: 0) {
            0 -> return answer["Envelope"] ?: JsonArray(emptyList())
            100, 108, 154 -> throw DiaryException("eduVulcan nie rozpoznaje już tego połączenia (może zostało odłączone w eduVulcan). Połącz ponownie.")
            201, 202, 204 -> throw DiaryException("Zgoda z eduVulcan wygasła albo została już użyta. Zaloguj się jeszcze raz.")
            else -> throw DiaryException("eduVulcan zgłosił błąd ($code: ${status?.text("Message").orEmpty()}).")
        }
    }

    private fun body(envelope: JsonElement): String {
        val now = ZonedDateTime.now(WARSAW)
        return buildJsonObject {
            put("AppName", APP_NAME)
            put("AppVersion", APP_VERSION)
            put("NotificationToken", "")
            put("API", 1)
            put("RequestId", UUID.randomUUID().toString())
            put("Timestamp", now.toEpochSecond())
            put("TimestampFormatted", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            put("Envelope", envelope)
        }.toString()
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    companion object {
        const val APP_NAME = "DzienniczekPlus 3.0"
        const val APP_VERSION = "25.09.24 (G)"
        const val APP_VERSION_CODE = "802"
        const val API_BASE_URL = "https://lekcjaplus.vulcan.net.pl"
        private val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")

        /** The unit ("tenant") a token from /api/ap belongs to: from its JWT payload (the signature is not checked). */
        internal fun tenantOf(token: String): String {
            val payload = token.split('.').getOrNull(1) ?: throw DiaryException("Nieprawidłowa odpowiedź eduVulcan.")
            val claims = runCatching { Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))).jsonObject }
                .getOrNull() ?: throw DiaryException("Nieprawidłowa odpowiedź eduVulcan.")
            return claims.text("tenant").ifBlank { throw DiaryException("Nieprawidłowa odpowiedź eduVulcan.") }
        }

        /**
         * Lessons from mobile/schedule/withchanges/byPupil. A substitution brings the new subject, room or teacher and
         * tells whether the lesson is cancelled; of two entries in one slot the one that takes place wins.
         */
        internal fun parseSchedule(entries: JsonArray): List<DiaryLesson> = entries.map { it.jsonObject }.mapNotNull { entry ->
            val date = entry.text("DateAt").take(10).ifBlank { return@mapNotNull null }.let(LocalDate::parse)
            val slot = entry["TimeSlot"].obj() ?: return@mapNotNull null
            var subject = entry["Subject"].obj()?.text("Name")?.ifBlank { null } ?: entry.text("Event")
            var room = entry["Room"].obj()?.text("Code").orEmpty()
            var teacher = entry["TeacherPrimary"].obj()?.text("DisplayName").orEmpty()
            val change = entry["Substitution"].obj()?.let { sub ->
                sub["Subject"].obj()?.text("Name")?.ifBlank { null }?.let { subject = it }
                sub["Room"].obj()?.text("Code")?.ifBlank { null }?.let { room = it }
                val substitute = sub["TeacherPrimary"].obj()?.text("DisplayName")?.ifBlank { null }
                val effect = sub.text("TeacherAbsenceEffectName")
                val cancelled = sub["ClassAbsence"]?.jsonPrimitive?.booleanOrNull == true ||
                    sub["Change"].obj()?.get("Type")?.jsonPrimitive?.intOrNull == 1 || "odwoł" in effect.lowercase()
                val movedTo = sub.text("ChangeDateAt").take(10).ifBlank { null }?.let(LocalDate::parse)?.takeIf { it != date }
                val kind = when {
                    cancelled || movedTo != null -> ChangeKind.CANCELLED
                    substitute != null && substitute != teacher -> ChangeKind.SUBSTITUTION
                    else -> ChangeKind.CHANGED
                }
                if (substitute != null) teacher = substitute
                val note = listOfNotNull(
                    movedTo?.let { "przeniesiona na ${it.dayOfMonth}.${it.monthValue}" },
                    effect, sub.text("Reason"), sub.text("PupilNote"),
                ).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" · ")
                LessonChange(kind, note)
            }
            DiaryLesson(date, slot["Position"]?.jsonPrimitive?.intOrNull ?: 0, subject, room, teacher, slot.text("Display"), change)
        }.groupBy { it.date to it.position }.values.map { slot -> slot.firstOrNull { it.change?.kind != ChangeKind.CANCELLED } ?: slot.first() }
            .sortedWith(compareBy({ it.date }, { it.position }))

        private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
        private fun JsonElement?.array(): List<JsonElement> = (this as? JsonArray).orEmpty()
        private fun JsonObject.text(name: String): String = (get(name) as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}
