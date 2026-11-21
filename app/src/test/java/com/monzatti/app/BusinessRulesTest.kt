package com.monzatti.app

import com.monzatti.app.data.fake.FakeCartRepository
import com.monzatti.app.data.fake.FakeProductRepository
import com.monzatti.app.domain.shop.ShopRules
import com.monzatti.app.domain.shop.mxn
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
    fun cartRejectsOutOfStockAndCountsUnits() = runTest {
        val catalog = FakeProductRepository()
        val cart = FakeCartRepository(catalog).apply { simulatedDelayMs = 0 }
        val soldOut = catalog.products.value.first { it.totalStock == 0 }
        assertTrue(cart.add(soldOut.id, "M").exceptionOrNull() is OutOfStockException)
        assertTrue(cart.lines.value.isEmpty())

        val available = catalog.products.value.first { it.isAvailable("M") }
        assertTrue(cart.add(available.id, "M").isSuccess)
        assertTrue(cart.add(available.id, "M").isSuccess)
        assertEquals(1, cart.lines.value.size)
        assertEquals(2, cart.lines.value.single().quantity)
    }

    @Test
    fun cannotAddMoreThanStock() = runTest {
        val catalog = FakeProductRepository()
        val cart = FakeCartRepository(catalog).apply { simulatedDelayMs = 0 }
        val product = catalog.products.value.first { it.id == "p2" } // Vestido midi: 2 en M
        assertTrue(cart.add(product.id, "M", 2).isSuccess)
        assertTrue(cart.add(product.id, "M", 1).isFailure)
    }

    @Test
    fun checkoutNeedsReservationAndDecrementsStock() = runTest {
        val catalog = FakeProductRepository()
        val cart = FakeCartRepository(catalog).apply { simulatedDelayMs = 0 }
        cart.add("p1", "M", 2)
        // Sin reserva previa (RN-02) no hay pago.
        assertTrue(cart.confirmOrder("TEST", "Calle 1").isFailure)

        assertTrue(cart.startCheckout().isSuccess)
        val order = cart.confirmOrder("TEST", "Calle 1").getOrThrow()
        assertEquals(2 * 2290, order.subtotal)
        assertEquals(ShopRules.commission(4580), order.commission)
        assertEquals(4, catalog.stockOf("p1", "M"))
        assertTrue(cart.lines.value.isEmpty())
        assertEquals(1, cart.orders.value.size)
    }

    @Test
    fun shopRules() {
        assertEquals(550, ShopRules.commission(4580))
        assertEquals(3, ShopRules.clampQuantity(5, 3))
        assertEquals(1, ShopRules.clampQuantity(0, 3))
        assertEquals(ShopRules.MAX_QUANTITY, ShopRules.clampQuantity(50, 99))
        assertEquals("$2,290 MXN", mxn(2290))
    }

    @Test
    fun passwordRules() {
        assertTrue(PasswordRules.isValid("Monzatti1"))
        assertFalse(PasswordRules.isValid("monzatti1"))
        assertFalse(PasswordRules.isValid("Monzatti"))
        assertFalse(PasswordRules.isValid("Mz1"))
    }
}
