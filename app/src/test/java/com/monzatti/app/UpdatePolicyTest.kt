package com.monzatti.app

import com.monzatti.app.domain.update.AppRelease
import com.monzatti.app.domain.update.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private fun release(code: Int, mandatory: Boolean = false, notes: String = "") =
        AppRelease(code, "1.$code.0", "https://x/monzatti-$code.apk", null, notes, mandatory)

    @Test
    fun `sin versiones nuevas no hay actualizacion`() {
        assertNull(UpdatePolicy.pick(3, listOf(release(2), release(3))))
    }

    @Test
    fun `ofrece la version mas nueva`() {
        val update = UpdatePolicy.pick(1, listOf(release(2), release(4), release(3)))!!
        assertEquals(4, update.release.versionCode)
        assertFalse(update.mandatory)
    }

    @Test
    fun `una obligatoria intermedia vuelve obligatoria la actualizacion`() {
        val update = UpdatePolicy.pick(1, listOf(release(2, mandatory = true), release(3)))!!
        assertEquals(3, update.release.versionCode)
        assertTrue(update.mandatory)
        // Pero si ya tienes la obligatoria, la siguiente es opcional.
        assertFalse(UpdatePolicy.pick(2, listOf(release(2, mandatory = true), release(3)))!!.mandatory)
    }

    @Test
    fun `junta las notas de la mas nueva a la mas vieja`() {
        val update = UpdatePolicy.pick(1, listOf(release(2, notes = "Carrito"), release(3, notes = " Espejo "), release(4)))!!
        assertEquals(listOf("1.3.0" to "Espejo", "1.2.0" to "Carrito"), update.notes)
    }

    @Test
    fun `posponer oculta el aviso un dia salvo obligatorias o versiones nuevas`() {
        val day = UpdatePolicy.SNOOZE_MILLIS
        val optional = UpdatePolicy.pick(1, listOf(release(2)))!!
        assertFalse(UpdatePolicy.shouldPrompt(optional, snoozedVersion = 2, snoozedAt = 1_000, now = 1_000 + day - 1))
        assertTrue(UpdatePolicy.shouldPrompt(optional, snoozedVersion = 2, snoozedAt = 1_000, now = 1_000 + day))
        assertTrue(UpdatePolicy.shouldPrompt(optional, snoozedVersion = 1, snoozedAt = 1_000, now = 1_001))
        val mandatory = UpdatePolicy.pick(1, listOf(release(2, mandatory = true)))!!
        assertTrue(UpdatePolicy.shouldPrompt(mandatory, snoozedVersion = 2, snoozedAt = 1_000, now = 1_001))
    }

    @Test
    fun `solo instala esta app en la version anunciada y con la huella correcta`() {
        val r = release(5)
        assertTrue(UpdatePolicy.isExpectedPackage("com.monzatti.app", "com.monzatti.app", 5, r))
        assertFalse(UpdatePolicy.isExpectedPackage("com.monzatti.app", "com.otra.app", 5, r))
        assertFalse(UpdatePolicy.isExpectedPackage("com.monzatti.app", "com.monzatti.app", 4, r))
        assertFalse(UpdatePolicy.isExpectedPackage("com.monzatti.app", null, null, r))
        assertFalse(UpdatePolicy.sha256Matches(null, "abc"))
        assertFalse(UpdatePolicy.sha256Matches("  ", "abc"))
        assertTrue(UpdatePolicy.sha256Matches("ABC", "abc"))
        assertFalse(UpdatePolicy.sha256Matches("abd", "abc"))
    }
}
