package com.jaynak.app.design

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * جدول رموز SF Symbols المستخدمة في تطبيق iOS مقابل أيقونات Material.
 * مكانٌ واحد للتعديل: أي أيقونة لا تبدو مطابقة تُبدَّل هنا فقط.
 */
fun sfVector(name: String): ImageVector = when (name) {
    "antenna.radiowaves.left.and.right", "dot.radiowaves.left.and.right" -> Icons.Filled.Sensors
    "arrow.up.left.and.arrow.down.right" -> Icons.Filled.Fullscreen
    "arrow.clockwise", "arrow.clockwise.circle.fill" -> Icons.Filled.Refresh
    "arrow.down" -> Icons.Filled.ArrowDownward
    "arrow.down.circle.fill" -> Icons.Filled.ArrowCircleDown
    "arrow.down.left" -> Icons.Filled.SouthWest
    "arrow.down.right" -> Icons.Filled.SouthEast
    "arrow.forward.circle.fill", "arrow.right.circle.fill" -> Icons.Filled.ArrowCircleRight
    "arrow.left.arrow.right" -> Icons.Filled.SwapHoriz
    "arrow.left.circle.fill" -> Icons.Filled.ArrowCircleLeft
    "arrow.right.to.line.circle.fill" -> Icons.Filled.Login
    "arrow.triangle.branch" -> Icons.Filled.CallSplit
    "arrow.triangle.turn.up.right.diamond.fill" -> Icons.Filled.Directions
    "arrow.up" -> Icons.Filled.ArrowUpward
    "arrow.up.arrow.down" -> Icons.Filled.SwapVert
    "arrow.up.circle", "arrow.up.circle.fill" -> Icons.Filled.ArrowCircleUp
    "arrow.up.left.square" -> Icons.Filled.NorthWest
    "arrow.up.right" -> Icons.Filled.NorthEast
    "arrow.uturn.backward" -> Icons.Filled.Undo
    "bag.fill" -> Icons.Filled.ShoppingBag
    "banknote.fill" -> Icons.Filled.Payments
    "bell.fill" -> Icons.Filled.Notifications
    "speaker.wave.2.fill" -> Icons.Filled.Notifications
    "bolt.fill" -> Icons.Filled.Bolt
    "bubble.left.and.bubble.right", "bubble.left.and.bubble.right.fill",
    "bubble.left.and.text.bubble.right.fill" -> Icons.Filled.Forum
    "bubble.left.fill" -> Icons.Filled.ChatBubble
    "calendar", "calendar.badge.clock" -> Icons.Filled.CalendarMonth
    "camera.fill" -> Icons.Filled.PhotoCamera
    "car", "car.fill" -> Icons.Filled.DirectionsCar
    "cart.fill" -> Icons.Filled.ShoppingCart
    "chart.bar.doc.horizontal" -> Icons.Filled.Assessment
    "chart.bar.fill" -> Icons.Filled.BarChart
    "chart.line.downtrend.xyaxis" -> Icons.Filled.TrendingDown
    "chart.line.uptrend.xyaxis" -> Icons.Filled.TrendingUp
    "chart.pie.fill" -> Icons.Filled.PieChart
    "checkmark" -> Icons.Filled.Check
    "checkmark.circle", "checkmark.circle.fill" -> Icons.Filled.CheckCircle
    "checkmark.seal.fill" -> Icons.Filled.Verified
    "chevron.backward" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
    "chevron.forward" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
    "chevron.left" -> Icons.Filled.ChevronLeft
    "chevron.down" -> Icons.Filled.KeyboardArrowDown
    "chevron.up" -> Icons.Filled.KeyboardArrowUp
    "circle" -> Icons.Filled.RadioButtonUnchecked
    "circle.fill" -> Icons.Filled.Circle
    "clock", "clock.badge.checkmark", "clock.badge.exclamationmark.fill" -> Icons.Filled.Schedule
    "clock.fill" -> Icons.Filled.AccessTimeFilled
    "clock.arrow.circlepath" -> Icons.Filled.History
    "creditcard.fill" -> Icons.Filled.CreditCard
    "crown.fill" -> Icons.Filled.WorkspacePremium
    "doc.badge.gearshape", "doc.text", "doc.text.fill" -> Icons.Filled.Description
    "doc.on.doc.fill" -> Icons.Filled.ContentCopy
    "doc.richtext.fill" -> Icons.Filled.Article
    "doc.text.magnifyingglass" -> Icons.Filled.FindInPage
    "ellipsis", "ellipsis.circle" -> Icons.Filled.MoreHoriz
    "envelope.badge.fill" -> Icons.Filled.MarkEmailUnread
    "envelope.fill" -> Icons.Filled.Email
    "exclamationmark.circle.fill" -> Icons.Filled.Error
    "exclamationmark.octagon.fill" -> Icons.Filled.Report
    "exclamationmark.shield.fill" -> Icons.Filled.GppMaybe
    "exclamationmark.triangle.fill" -> Icons.Filled.Warning
    "eye.fill" -> Icons.Filled.Visibility
    "flag.checkered", "flag.fill" -> Icons.Filled.Flag
    "gift", "gift.fill" -> Icons.Filled.CardGiftcard
    "globe" -> Icons.Filled.Language
    "hammer.fill" -> Icons.Filled.Build
    "hand.raised.fill", "hand.raised.slash" -> Icons.Filled.PanTool
    "hand.thumbsup.fill" -> Icons.Filled.ThumbUp
    "house.fill" -> Icons.Filled.Home
    "info", "info.circle", "info.circle.fill" -> Icons.Filled.Info
    "largecircle.fill.circle" -> Icons.Filled.RadioButtonChecked
    "lifepreserver.fill" -> Icons.Filled.SupportAgent
    "headphones" -> Icons.Filled.Headset
    "lightbulb.fill" -> Icons.Filled.Lightbulb
    "list.bullet.rectangle", "list.bullet.rectangle.fill",
    "list.bullet.rectangle.portrait.fill" -> Icons.AutoMirrored.Filled.ViewList
    "location.fill" -> Icons.Filled.MyLocation
    "location.north.line.fill" -> Icons.Filled.Navigation
    "lock.fill" -> Icons.Filled.Lock
    "lock.shield.fill" -> Icons.Filled.Security
    "magnifyingglass" -> Icons.Filled.Search
    "map.fill" -> Icons.Filled.Map
    "mappin", "mappin.and.ellipse" -> Icons.Filled.LocationOn
    "mappin.circle.fill" -> Icons.Filled.Place
    "message.badge.filled.fill" -> Icons.Filled.MarkUnreadChatAlt
    "message.fill" -> Icons.Filled.Message
    "minus" -> Icons.Filled.Remove
    "minus.circle.fill" -> Icons.Filled.RemoveCircle
    "moon.zzz.fill" -> Icons.Filled.Bedtime
    "paperplane.fill" -> Icons.AutoMirrored.Filled.Send
    "pencil", "pencil.line" -> Icons.Filled.Edit
    "percent" -> Icons.Filled.Percent
    "person", "person.fill" -> Icons.Filled.Person
    "person.2.badge.key.fill" -> Icons.Filled.ManageAccounts
    "person.2.fill" -> Icons.Filled.Group
    "person.badge.clock.fill" -> Icons.Filled.HourglassTop
    "person.badge.plus", "person.badge.plus.fill" -> Icons.Filled.PersonAdd
    "person.badge.shield.checkmark.fill" -> Icons.Filled.AdminPanelSettings
    "person.crop.circle.fill" -> Icons.Filled.AccountCircle
    "person.fill.xmark" -> Icons.Filled.PersonOff
    "person.text.rectangle.fill" -> Icons.Filled.Badge
    "phone", "phone.fill" -> Icons.Filled.Phone
    "photo" -> Icons.Filled.Image
    "photo.on.rectangle.angled" -> Icons.Filled.PhotoLibrary
    "plus" -> Icons.Filled.Add
    "plus.circle.fill" -> Icons.Filled.AddCircle
    "plus.forwardslash.minus" -> Icons.Filled.Calculate
    "point.topleft.down.to.point.bottomright.curvepath" -> Icons.Filled.Route
    "questionmark.circle" -> Icons.Filled.Help
    "rectangle.and.text.magnifyingglass" -> Icons.Filled.ManageSearch
    "rectangle.portrait.and.arrow.right" -> Icons.AutoMirrored.Filled.Logout
    "rosette" -> Icons.Filled.MilitaryTech
    "safari.fill" -> Icons.Filled.Explore
    "server.rack" -> Icons.Filled.Dns
    "shield.fill", "shield.lefthalf.filled" -> Icons.Filled.Shield
    "shield.slash.fill", "xmark.shield.fill" -> Icons.Filled.GppBad
    "shippingbox", "shippingbox.fill" -> Icons.Filled.Inventory2
    "slider.horizontal.3" -> Icons.Filled.Tune
    "sparkles" -> Icons.Filled.AutoAwesome
    "square.grid.2x2.fill" -> Icons.Filled.GridView
    "star" -> Icons.Filled.StarBorder
    "star.fill" -> Icons.Filled.Star
    "star.circle.fill" -> Icons.Filled.Stars
    "tag.fill" -> Icons.Filled.LocalOffer
    "text.alignright" -> Icons.Filled.FormatAlignRight
    "text.bubble", "text.bubble.fill" -> Icons.Filled.Chat
    "ticket", "ticket.fill" -> Icons.Filled.ConfirmationNumber
    "hourglass" -> Icons.Filled.HourglassTop
    "trash", "trash.fill" -> Icons.Filled.Delete
    "trophy.fill" -> Icons.Filled.EmojiEvents
    "wallet.bifold.fill" -> Icons.Filled.AccountBalanceWallet
    "xmark" -> Icons.Filled.Close
    "xmark.circle", "xmark.circle.fill", "xmark.octagon.fill", "xmark.seal.fill" -> Icons.Filled.Cancel
    else -> Icons.Filled.Circle
}

/** مقابل Image(systemName:) — حجم بالـ dp ولون المحتوى الحالي افتراضياً. */
@Composable
fun SFIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = sfVector(name),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
    )
}
