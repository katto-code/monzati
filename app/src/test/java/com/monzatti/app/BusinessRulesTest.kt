package com.monzatti.app

import com.monzatti.app.data.fake.FakeProductRepository
import com.monzatti.app.domain.model.OutOfStockException
import com.monzatti.app.domain.model.PasswordRules
import com.monzatti.app.domain.model.Product
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessRulesTest {

    private val product = Product("p", "Marca", "Blusa", "👚", "Blusas", 100, mapOf("S" to 0, "M" to 3))

    @Test
    fun cannotBuySizeWithZeroStock() {
        assertFalse(product.isAvailable("S"))
        assertFalse(product.isAvailable("XL"))
        assertTrue(product.isAvailable("M"))
    }

    @Test
    fun lowStockFlag() {
        assertTrue(product.isLowStock)
        assertFalse(product.copy(stockBySize = mapOf("M" to 0)).isLowStock)
    }

    @Test
    fun repositoryRejectsOutOfStock() = runTest {
        val repo = FakeProductRepository()
        val soldOut = repo.products.value.first { it.totalStock == 0 }
        val result = repo.addToBag(soldOut.id, "M")
        assertTrue(result.exceptionOrNull() is OutOfStockException)
        assertEquals(0, repo.bagCount.value)

        val available = repo.products.value.first { it.isAvailable("M") }
        assertTrue(repo.addToBag(available.id, "M").isSuccess)
        assertEquals(1, repo.bagCount.value)
    }

    @Test
    fun passwordRules() {
        assertTrue(PasswordRules.isValid("Monzatti1"))
        assertFalse(PasswordRules.isValid("monzatti1"))
        assertFalse(PasswordRules.isValid("Monzatti"))
        assertFalse(PasswordRules.isValid("Mz1"))
    }
}
