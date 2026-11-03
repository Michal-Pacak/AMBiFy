package pl.ambif.ambify.server

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.ambif.ambify.server.timetable.DiarySchedule
import pl.ambif.ambify.server.timetable.diary.EduVulcan
import pl.ambif.ambify.server.timetable.diary.VulcanCredential
import pl.ambif.ambify.server.timetable.diary.VulcanSigner
import pl.ambif.ambify.shared.ChangeKind

class EduVulcanTest {
    /** Made once by the Python iris library (vulcan_digest's venv) with a throwaway key: the Kotlin port must sign the same. */
    private val iris = Json.parseToJsonElement(javaClass.getResource("/vulcan/iris-signature.json")!!.readText()).jsonObject
    private fun iris(name: String) = iris.getValue(name).jsonPrimitive.content

    private val credential = VulcanCredential(
        restUrls = emptyList(),
        certificate = iris("certificate"),
        privateKey = iris("privateKey"),
        fingerprint = iris("fingerprint"),
        deviceId = "test",
    )

    @Test
    fun signsLikeIris() {
        assertEquals(iris("fingerprint"), VulcanSigner.fingerprint(iris("certificate")))
        assertEquals(iris("canonical"), VulcanSigner.canonicalUrl(iris("url")))

        val date = "Sat, 31 Oct 2026 19:00:00 GMT"
        val digest = iris("digest").removePrefix("SHA-256=")
        assertEquals(iris("signature"), VulcanSigner.signature(listOf("vCanonicalUrl" to iris("canonical"), "Digest" to digest, "vDate" to date), credential))
        assertEquals(iris("signatureGet"), VulcanSigner.signature(listOf("vCanonicalUrl" to iris("canonical"), "vDate" to date), credential))

        val headers = VulcanSigner.headers(iris("url"), iris("body"), 42, credential, ZonedDateTime.of(2026, 10, 31, 19, 0, 0, 0, ZoneOffset.UTC))
        assertEquals(iris("digest"), headers["digest"])
        assertEquals(iris("signature"), headers["signature"])
        assertEquals("42", headers["vhint"])
    }

    @Test
    fun ownKeysSignAndAreFoundAgain() {
        val made = VulcanCredential.create()
        assertEquals(made.fingerprint, VulcanSigner.fingerprint(made.certificate))
        VulcanSigner.signature(listOf("vDate" to "x"), made)
    }

    @Test
    fun tenantFromTheToken() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"tenant":"warszawa","name":"Bartosz"}""".toByteArray())
        assertEquals("warszawa", EduVulcan.tenantOf("eyJhbGciOiJSUzI1NiJ9.$payload.podpis"))
    }

    @Test
    fun scheduleWithChanges() {
        val lessons = EduVulcan.parseSchedule(Json.parseToJsonElement(SCHEDULE).jsonArray)
        assertEquals(4, lessons.size)
        val (polish, maths, nature, pe) = lessons

        assertEquals(LocalDate.of(2026, 11, 2), polish.date)
        assertEquals("Język polski", polish.subject)
        assertEquals("08:00-08:45", polish.time)
        assertNull(polish.change)

        // Another teacher: a substitution, with the teacher and room from the substitution.
        assertEquals(ChangeKind.SUBSTITUTION, maths.change?.kind)
        assertEquals("Anna Nowak", maths.teacher)
        assertEquals("21", maths.room)
        assertEquals("Nieobecność nauczyciela", maths.change?.note)

        assertEquals(ChangeKind.CANCELLED, nature.change?.kind)
        assertEquals("Zajęcia odwołane", nature.change?.note)

        // Moved to another day: cancelled here, and the cancelled copy loses to the lesson that takes place in that slot.
        assertEquals("Wychowanie fizyczne", pe.subject)
        assertNull(pe.change)
    }

    @Test
    fun nextUpdateAtEighteen() {
        val warsaw = java.time.ZoneId.of("Europe/Warsaw")
        assertEquals(60L, DiarySchedule.delayToNext(ZonedDateTime.of(2026, 10, 31, 17, 0, 0, 0, warsaw)).toMinutes())
        assertEquals(23 * 60L, DiarySchedule.delayToNext(ZonedDateTime.of(2026, 10, 31, 19, 0, 0, 0, warsaw)).toMinutes())
    }

    private companion object {
        fun slot(position: Int, time: String) = """{"Id":$position,"Start":"08:00","End":"08:45","Display":"$time","Position":$position}"""

        // Shaped like eduVulcan's answer (the fields the server reads), with made-up people.
        val SCHEDULE = """
        [
          {"Id":1,"DateAt":"2026-11-02","TimeSlot":${slot(1, "08:00-08:45")},"Subject":{"Name":"Język polski"},
           "Room":{"Id":1,"Code":"12"},"TeacherPrimary":{"DisplayName":"Jan Kowalski"},"Substitution":null},
          {"Id":2,"DateAt":"2026-11-02","TimeSlot":${slot(2, "08:55-09:40")},"Subject":{"Name":"Matematyka"},
           "Room":{"Id":2,"Code":"14"},"TeacherPrimary":{"DisplayName":"Ewa Zielińska"},
           "Substitution":{"ClassAbsence":false,"ChangeDateAt":null,"Reason":null,"PupilNote":null,
             "TeacherAbsenceEffectName":"Nieobecność nauczyciela","Room":{"Code":"21"},"TeacherPrimary":{"DisplayName":"Anna Nowak"},
             "Change":{"Id":7,"Type":2,"IsMerge":false,"Separation":false}}},
          {"Id":3,"DateAt":"2026-11-02","TimeSlot":${slot(3, "09:50-10:35")},"Subject":{"Name":"Przyroda"},
           "Room":{"Id":3,"Code":"15"},"TeacherPrimary":{"DisplayName":"Piotr Wiśniewski"},
           "Substitution":{"ClassAbsence":true,"TeacherAbsenceEffectName":"Zajęcia odwołane","Change":{"Type":1}}},
          {"Id":4,"DateAt":"2026-11-02","TimeSlot":${slot(4, "10:55-11:40")},"Subject":{"Name":"Muzyka"},
           "Room":null,"TeacherPrimary":null,"Substitution":{"ChangeDateAt":"2026-11-05","Change":{"Type":3}}},
          {"Id":5,"DateAt":"2026-11-02","TimeSlot":${slot(4, "10:55-11:40")},"Subject":{"Name":"Wychowanie fizyczne"},
           "Room":{"Code":"sala gim."},"TeacherPrimary":{"DisplayName":"Tomasz Lis"}}
        ]
        """
    }
}
