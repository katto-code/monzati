package com.monzatti.app.domain.shop

import kotlinx.coroutines.flow.StateFlow

/** Una partida del carrito: un producto en una talla. */
data class CartLine(
    val id: String,
    val productId: String,
    val name: String,
    val brand: String,
    val emoji: String,
    val color: String,
    val size: String,
    val unitPrice: Int,
    val quantity: Int,
    /** Existencias actuales de la talla; sirve para limitar el "+". */
    val stock: Int
) {
    val total: Int get() = unitPrice * quantity
}

data class OrderLine(val name: String, val brand: String, val size: String, val quantity: Int, val unitPrice: Int)

data class Order(
    val id: String,
    /** Fecha legible (ya formateada). */
    val createdAt: String,
    val status: String,
    val lines: List<OrderLine>,
    val subtotal: Int,
    val commission: Int,
    val total: Int
) {
    val shortId: String get() = "MZ-" + id.replace("-", "").takeLast(6).uppercase()
    val units: Int get() = lines.sumOf { it.quantity }
}

data class SalesSummary(val orders: Int, val units: Int, val gross: Int, val commission: Int, val net: Int)

/** Reglas de la tienda (S0-09). */
object ShopRules {
    /** RN-01: comisión de Monzatti, dentro del rango 10–15 % del importe neto. */
    const val COMMISSION_RATE = 0.12

    /** RN-02: minutos que se apartan las prendas al iniciar el pago. */
    const val RESERVATION_MINUTES = 10

    const val MAX_QUANTITY = 10

    fun subtotal(lines: List<CartLine>): Int = lines.sumOf { it.total }

    /** Envío gratis en el prototipo (marcas ficticias). */
    fun shipping(lines: List<CartLine>): Int = 0

    fun total(lines: List<CartLine>): Int = subtotal(lines) + shipping(lines)

    fun commission(subtotal: Int): Int = Math.round(subtotal * COMMISSION_RATE).toInt()

    /** Cantidad permitida: entre 1 y el mínimo de existencias y el máximo por partida. */
    fun clampQuantity(quantity: Int, stock: Int): Int = quantity.coerceIn(1, maxOf(1, minOf(stock, MAX_QUANTITY)))
}

interface CartRepository {
    val lines: StateFlow<List<CartLine>>
    val orders: StateFlow<List<Order>>

    suspend fun refresh(): Result<Unit>
    suspend fun add(productId: String, size: String, quantity: Int = 1): Result<Unit>
    suspend fun setQuantity(lineId: String, quantity: Int): Result<Unit>
    suspend fun remove(lineId: String): Result<Unit>

    /** RN-02: aparta todo el carrito. Devuelve el instante (epoch ms) en que vence la reserva. */
    suspend fun startCheckout(): Result<Long>

    /** RN-02: libera la reserva al salir del pago sin pagar. */
    suspend fun cancelCheckout(): Result<Unit>

    /** Pago de prueba: convierte la reserva en pedido pagado y vacía el carrito. */
    suspend fun confirmOrder(paymentRef: String, shippingAddress: String): Result<Order>

    suspend fun refreshOrders(): Result<Unit>
}

/** Formato de dinero: $2,290 MXN */
fun mxn(amount: Int): String = "$" + String.format(java.util.Locale.US, "%,d", amount) + " MXN"
