package com.jaynak.app.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.UUID

/** مقابل Core/ShowcaseData.swift — تعبئة بيانات العرض للقطات المتجر (للأدمن التجريبي فقط). */
fun AppStore.fillShowcaseData() {
    if (!AppConfig.demoAdminBypassEnabled) return

    val places = Geo.places
    val newCustomers = mutableListOf<Customer>()
    val newDrivers = mutableListOf<Driver>()

    val customerSeed = listOf(
        Triple("نواف الحسني", "91112233", 12.750) to 640,
        Triple("منيرة الزدجالية", "92223344", 3.200) to 210,
        Triple("أحمد البوسعيدي", "79334455", 0.500) to 90,
    )
    for ((t, pts) in customerSeed) {
        newCustomers.add(
            Customer(
                name = t.first, phone = t.second, walletBalance = t.third,
                points = pts, lifetimePoints = pts + 300,
                savedPlaces = listOf(places[9], places[8]),
            )
        )
    }

    class DSeed(
        val name: String, val phone: String, val zone: String, val coord: Coord,
        val plate: String, val make: String, val model: String,
        val completed: Int, val ratingSum: Int, val ratingCount: Int, val tierPoints: Int,
        val earnings: Double, val approval: DriverApproval, val presence: DriverPresence,
    )

    val driverSeed = listOf(
        DSeed("عبدالله الهنائي", "99887766", "بوشر", Coord(23.5890, 58.4150), "12345 أ", "تويوتا", "كورولا", 194, 923, 194, 2450, 86.400, DriverApproval.approved, DriverPresence.online),
        DSeed("بدر الرواحي", "79988776", "مسقط", Coord(23.6120, 58.4700), "31007 د", "هوندا", "سيفيك", 241, 1180, 241, 1150, 133.250, DriverApproval.approved, DriverPresence.online),
        DSeed("يوسف البلوشي", "99661122", "مطرح", Coord(23.6050, 58.5300), "22881 ب", "نيسان", "صني", 152, 705, 152, 520, 42.150, DriverApproval.approved, DriverPresence.online),
        DSeed("فهد السيابي", "95544332", "السيب", Coord(23.5900, 58.3200), "77410 ج", "كيا", "سيراتو", 92, 431, 92, 180, 21.900, DriverApproval.approved, DriverPresence.online),
        DSeed("طلال الشحي", "91122334", "بوشر", Coord(23.5820, 58.3600), "40551 ز", "تويوتا", "يارس", 0, 0, 0, 0, 0.0, DriverApproval.pending, DriverPresence.offline),
    )
    for (d in driverSeed) {
        val assets = mutableListOf<DocumentAsset>()
        for (kind in listOf(DocumentKind.civilIDFront, DocumentKind.license, DocumentKind.ownership,
            DocumentKind.vehicle, DocumentKind.vehicle)) {
            val data = placeholderJpeg() ?: continue
            val asset = try { DocumentStore.save(data, kind, "image/jpeg") } catch (_: Throwable) { null } ?: continue
            assets.add(
                asset.copy(
                    state = DocumentState.uploaded,
                    remoteID = "showcase-" + UUID.randomUUID().toString().take(8).uppercase(),
                    uploadedAt = nowMs(),
                )
            )
        }
        newDrivers.add(
            Driver(
                name = d.name, phone = d.phone, zone = d.zone, coord = d.coord,
                vehicle = Vehicle(
                    make = d.make, model = d.model, year = "2022", plateNumber = d.plate,
                    color = "أبيض", ownershipNumber = "OW-" + d.plate.filter { it.isDigit() },
                ),
                docs = DriverDocuments(
                    civilID = "2${d.phone}9", licenseNumber = "L-${d.phone.takeLast(5)}", assets = assets,
                ),
                approval = d.approval, presence = d.presence,
                completedOrders = d.completed, ratingSum = d.ratingSum, ratingCount = d.ratingCount,
                tierPoints = d.tierPoints, earningsBalance = d.earnings,
                offersReceived = (d.completed * 1.25).toInt(), offersAccepted = d.completed,
            )
        )
    }

    customers = newCustomers
    drivers = newDrivers

    class Plan(
        val daysAgo: Double, val hour: Int, val kind: OrderKind, val pickup: Int, val dropoff: Int,
        val customer: Int, val payment: PaymentMethod, val rating: Int,
    )

    val plan = listOf(
        Plan(0.2, 11, OrderKind.documents, 1, 9, 0, PaymentMethod.wallet, 5),
        Plan(0.4, 10, OrderKind.parcel, 0, 2, 1, PaymentMethod.cash, 4),
        Plan(0.9, 18, OrderKind.errand, 9, 10, 0, PaymentMethod.wallet, 5),
        Plan(1.1, 13, OrderKind.documents, 7, 5, 2, PaymentMethod.cash, 3),
        Plan(1.6, 9, OrderKind.parcel, 0, 11, 1, PaymentMethod.wallet, 5),
        Plan(2.2, 17, OrderKind.documents, 1, 6, 0, PaymentMethod.wallet, 4),
        Plan(2.7, 14, OrderKind.errand, 3, 9, 2, PaymentMethod.cash, 5),
        Plan(3.3, 12, OrderKind.parcel, 8, 4, 1, PaymentMethod.wallet, 4),
        Plan(3.8, 19, OrderKind.documents, 4, 1, 0, PaymentMethod.wallet, 5),
        Plan(4.4, 8, OrderKind.parcel, 9, 3, 2, PaymentMethod.cash, 5),
        Plan(4.9, 16, OrderKind.errand, 0, 8, 1, PaymentMethod.wallet, 3),
        Plan(5.5, 11, OrderKind.documents, 5, 2, 0, PaymentMethod.wallet, 5),
        Plan(6.3, 15, OrderKind.parcel, 10, 0, 1, PaymentMethod.cash, 4),
        Plan(6.8, 10, OrderKind.errand, 3, 10, 0, PaymentMethod.wallet, 5),
    )
    val recipientNames = listOf("أبو محمد", "المكتب", "الاستقبال", "م. سعيد")
    val recipientPhones = listOf("91501122", "99334455", "79778899", "92112233")

    val built = mutableListOf<Order>()
    val txns = mutableListOf<WalletTxn>()
    for ((n, p) in plan.withIndex()) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs() - (p.daysAgo * 86_400_000.0).toLong()
        cal.set(Calendar.HOUR_OF_DAY, p.hour)
        cal.set(Calendar.MINUTE, (n * 7) % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val created = cal.timeInMillis

        val pickup = places[p.pickup]
        val dropoff = places[p.dropoff]
        val price = PricingEngine.quote(pickup, dropoff, p.kind, rules, at = created)
        val delivered = created + price.minutes * 60_000L
        val driver = newDrivers[n % 4]
        val customer = newCustomers[p.customer]
        val o = Order(
            ref = "JK-${1000 + n}",
            customerID = customer.id, driverID = driver.id,
            kind = p.kind, pickup = pickup, dropoff = dropoff,
            payment = p.payment, price = price,
            status = OrderStatus.delivered,
            createdAt = created, acceptedAt = created + 38_000L, deliveredAt = delivered,
            driverCoord = dropoff.coord,
            customerRating = p.rating, driverRating = 5,
            recipientName = recipientNames[n % 4], recipientPhone = recipientPhones[n % 4],
        )
        built.add(o)

        if (p.payment == PaymentMethod.wallet) {
            txns.add(WalletTxn(ownerID = customer.id, kind = WalletTxnKind.orderPayment,
                amount = -price.total, date = delivered, orderRef = o.ref))
            txns.add(WalletTxn(ownerID = driver.id, kind = WalletTxnKind.orderPayment,
                amount = price.driverPayout, date = delivered, orderRef = o.ref))
        } else {
            txns.add(WalletTxn(ownerID = driver.id, kind = WalletTxnKind.commission,
                amount = -price.commission, date = delivered, orderRef = o.ref))
        }
    }
    txns.add(WalletTxn(ownerID = newCustomers[0].id, kind = WalletTxnKind.topUp, amount = 20.000,
        date = nowMs() - (6.9 * 86_400_000.0).toLong()))

    orders = built.sortedByDescending { it.createdAt }
    transactions = txns.sortedByDescending { it.date }

    promos = listOf(
        PromoCode(code = "JAYNAK25", percentOff = 0.25, maxDiscount = 0.700, timesUsed = 143),
        PromoCode(code = "OM10", percentOff = 0.10, maxDiscount = 0.300,
            newCustomersOnly = false, timesUsed = 412),
    )

    val c0 = newCustomers[0]
    val d2 = newDrivers[2]
    tickets = listOf(
        SupportTicket(
            ref = "SP-2041", openedBy = c0.id, openerName = c0.name, role = UserRole.customer,
            subject = "الطلب تأخر أكثر من المتوقع", status = TicketStatus.open,
            createdAt = nowMs() - 3_600_000L * 5,
            messages = listOf(ChatMessage(threadID = "ticket", senderID = c0.id, senderName = c0.name,
                isMine = true, body = "الطلب JK-1002 تأخر ٢٥ دقيقة عن الوقت المتوقع.")),
            orderRef = "JK-1002",
        ),
        SupportTicket(
            ref = "SP-2038", openedBy = d2.id, openerName = d2.name, role = UserRole.driver,
            subject = "لم يصل مبلغ السحب", status = TicketStatus.pending,
            createdAt = nowMs() - 3_600_000L * 26,
            messages = listOf(ChatMessage(threadID = "ticket", senderID = d2.id, senderName = d2.name,
                isMine = true, body = "طلبت سحب ٢٠ ر.ع. قبل يومين ولم يصل.")),
        ),
    )

    vouchers = listOf(
        Voucher(customerID = c0.id, rewardTitle = "توصيل مجاني", kind = RewardKind.freeDelivery,
            value = 1.0, maxDiscount = 3.000, expiresAt = nowMs() + 20 * DAY_MS)
    )

    toast = AppStore.Toast(text = "عُبِّئت بيانات العرض — للقطات المتجر", kind = AppStore.Toast.Kind.success)
    Feedback.success()
    save()
}

private fun placeholderJpeg(): ByteArray? = try {
    val bmp = Bitmap.createBitmap(240, 150, Bitmap.Config.ARGB_8888)
    Canvas(bmp).drawColor(Color.rgb(209, 209, 209))
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
    out.toByteArray()
} catch (_: Throwable) { null }
