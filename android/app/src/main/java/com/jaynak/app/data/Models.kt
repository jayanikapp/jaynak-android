package com.jaynak.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Point(
    val lat: Double,
    val lon: Double,
    val zone: String = "",
    val name: String = "",
)

@Serializable
data class Price(
    val km: Double = 0.0,
    val minutes: Int = 0,
    val base: Double = 0.0,
    val distance: Double = 0.0,
    val time: Double = 0.0,
    val zoneSurcharge: Double = 0.0,
    val kindAdjustment: Double = 0.0,
    val surge: Double = 0.0,
    val discount: Double = 0.0,
    val voucherTitle: String? = null,
    val total: Double = 0.0,

    val commission: Double = 0.0,
    val driverPayout: Double = 0.0,
    val isPeak: Boolean = false,
)

@Serializable
data class Order(
    val id: String,
    val ref: String = "",
    val kind: String = "errand",
    val status: String = "searching",
    val pickup: Point,
    val dropoff: Point,
    val price: Price = Price(),
    val payment: String = "cash",
    val note: String = "",
    val createdAt: Double = 0.0,
    val driverID: String? = null,
    val customerPhone: String = "",
    val recipientPhone: String = "",

    val km: Double? = null,
    val onPath: Boolean = false,

    val steps: List<String> = emptyList(),
    val stepIndex: Int = -1,
    val rating: Int? = null,
    val pointsEarned: Int? = null,
    val driver: DriverPublic? = null,
)


@Serializable
data class DriverPublic(
    val name: String = "",
    val phone: String = "",
    val zone: String = "",
    val rating: Double? = null,
    val completedOrders: Int = 0,
)

@Serializable
data class Customer(
    val id: String = "",
    val name: String = "",
    val walletBalance: Double = 0.0,
    val points: Int = 0,
    val lifetimePoints: Int = 0,
)

@Serializable
data class Driver(
    val id: String = "",
    val name: String = "",
    val approval: String = "pending",
    val presence: String = "offline",
    val earningsBalance: Double = 0.0,
    val completedOrders: Int = 0,
)

@Serializable
data class Me(
    val phone: String = "",
    val name: String = "",
    val isOwner: Boolean = false,
    val customer: Customer? = null,
    val driver: Driver? = null,
    val nextPayout: String? = null,
)

@Serializable
data class Tier(
    val id: String = "",
    val title: String = "",
    val commission: Double = 0.0,
    val points: Int = 0,
    val next: String? = null,
    val pointsToNext: Int? = null,
)

@Serializable
data class DriverFeed(
    val driver: Driver? = null,
    val active: Order? = null,
    val offers: List<Order> = emptyList(),
    val board: List<Order> = emptyList(),
    val destination: Point? = null,
    val acceptRadiusKm: Double = 3.0,
    val tier: Tier? = null,
)

@Serializable
data class Place(
    val name: String,
    val zone: String,
    val lat: Double,
    val lon: Double,
    val details: String = "",
)

@Serializable
data class Kind(val id: String, val title: String, val multiplier: Double = 1.0)

@Serializable
data class Places(
    val places: List<Place> = emptyList(),
    val zones: List<String> = emptyList(),
    val kinds: List<Kind> = emptyList(),
    val center: Point? = null,
)

@Serializable
data class Wallet(
    val balance: Double = 0.0,
    val points: Int = 0,
    val lifetimePoints: Int = 0,
    val rewards: List<Reward> = emptyList(),
)

@Serializable
data class Reward(
    val id: String = "",
    val title: String = "",
    val cost: Int = 0,
    val detail: String = "",
    val affordable: Boolean = false,
)


class ApiError(val code: String, override val message: String) : Exception(message)


@Serializable
data class AdminStats(
    val ordersToday: Int = 0,
    val revenueToday: Double = 0.0,
    val commissionToday: Double = 0.0,
    val live: Int = 0,
    val drivers: Int = 0,
    val online: Int = 0,
    val pending: Int = 0,
    val customers: Int = 0,
)

@Serializable
data class AdminDriver(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val zone: String = "",
    val approval: String = "pending",
    val presence: String = "offline",
    val earningsBalance: Double = 0.0,
    val completedOrders: Int = 0,
)

@Serializable
data class Rules(
    val baseFare: Double = 0.0,
    val perKm: Double = 0.0,
    val perMinute: Double = 0.0,
    val minimumFare: Double = 0.0,

    val fixedCommission: Double = 0.0,
    val minEarningsBalance: Double = 0.0,
)

@Serializable
data class AdminOverview(
    val stats: AdminStats = AdminStats(),
    val live: List<Order> = emptyList(),
    val drivers: List<AdminDriver> = emptyList(),
    val rules: Rules = Rules(),
    val nextPayout: String? = null,
)
