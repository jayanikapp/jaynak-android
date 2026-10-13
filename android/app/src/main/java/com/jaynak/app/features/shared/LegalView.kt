package com.jaynak.app.features.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppConfig
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.UserRole
import com.jaynak.app.core.money
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassCard
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBChip
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassSurface

/** مقابل LegalView.swift — نصوص الشروط والخصوصية كما هي حرفياً. */
@Composable
fun LegalView(initialAudience: UserRole? = UserRole.customer, onDismiss: () -> Unit) {
    var audience by remember { mutableIntStateOf(if (initialAudience == UserRole.driver) 1 else 0) }
    var tab by remember { mutableIntStateOf(0) }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.6f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green,
                        modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("الشروط والخصوصية", style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 20.dp))
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    SegmentedPicker(listOf("للعملاء", "للمندوبين"), audience, { audience = it },
                        Modifier.padding(top = 8.dp))
                    SegmentedPicker(listOf("الشروط", "الخصوصية", "الاسترجاع", "البيانات"), tab, { tab = it })

                    when (tab) {
                        0 -> if (audience == 0) CustomerTerms() else DriverTerms()
                        1 -> Privacy()
                        2 -> Refund()
                        else -> DataCollection()
                    }
                    CommonClauses()
                }
            }
        }
    }
}

@Composable
private fun LegalSection(title: String, items: List<String>) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp),
            horizontalAlignment = Alignment.End) {
            Text(title, style = MBType.headline, color = MB.ink)
            items.forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 7.dp).size(4.dp).background(MB.green, CircleShape))
                    Text(line, style = MBType.caption, color = MB.ink2,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CustomerTerms() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LegalSection("١. التعريفات", listOf(
            "«التطبيق»: تطبيق جاينك لتوصيل الطلبات.",
            "«العميل»: الشخص الذي يطلب الخدمة.",
            "«المندوب»: الشخص المسؤول عن استلام الطلب وتسليمه.",
        ))
        LegalSection("٢. استخدام الخدمة", listOf(
            "يجب أن تكون بيانات حسابك صحيحة ومحدّثة: الاسم والعنوان ورقم الهاتف.",
            "الحد الأدنى للطلب والرسوم تُحدَّد حسب المنطقة ونوع الطلب.",
            "جاينك وسيط بين العميل والمندوب، وغير مسؤول عن جودة المنتجات نفسها.",
        ))
        LegalSection("٣. الطلبات والدفع", listOf(
            "الدفع متاح نقداً عند التسليم أو إلكترونياً عبر المحفظة.",
            "إلغاء الطلب مسموح مجاناً قبل قبول المندوب له.",
            "بعد قبول المندوب قد تُطبَّق رسوم إلغاء قدرها ٢ ر.ع.",
            "إذا لم يجدك المندوب في العنوان، يحق له إلغاء الطلب بعد ١٠ دقائق انتظار.",
        ))
        LegalSection("٤. المسؤولية", listOf(
            "جاينك غير مسؤول عن التأخير الناتج عن ظروف خارجة عن الإرادة: الزحام، الطقس، الأعطال.",
            "للشكاوى: تواصل مع الدعم خلال ٢٤ ساعة من استلام الطلب.",
        ))
        LegalSection("٥. المحتوى والسلوك", listOf(
            "المحادثة داخل الطلب للتنسيق فقط.",
            "أي إساءة أو محتوى مخالف يُبلَّغ عنه من زر «إبلاغ» في المحادثة.",
            "يُمنع طلب نقل أي مواد محظورة قانوناً أو خطرة أو نقود.",
        ))
        LegalSection("٦. إنهاء الحساب", listOf(
            "يحق لنا إيقاف الحساب في حال الاحتيال أو سوء الاستخدام أو الإساءة للمندوبين.",
            "ويحق لك طلب حذف حسابك وبياناتك بالكامل من: حسابي ← حذف الحساب. يُنفَّذ الحذف بعد مراجعة الطلب من فريقنا.",
        ))
    }
}

@Composable
private fun DriverTerms() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LegalSection("١. التسجيل والأهلية", listOf(
            "ألا يقل العمر عن ${AppConfig.minDriverAge} سنة.",
            "رخصة قيادة سارية + تأمين ساري + بطاقة مدنية.",
            "توفير وسيلة نقل مناسبة: دراجة أو سيارة.",
            "اجتياز فحص الخلفية الأمنية.",
        ))
        LegalSection("٢. آلية العمل", listOf(
            "المندوب **شريك مستقل** وليس موظفاً لدى جاينك.",
            "قبول الطلبات اختياري، لكن انخفاض نسبة القبول قد يقلّل أولويتك في التوزيع.",
            "الالتزام بوقت الاستلام والتسليم المحدَّد في التطبيق.",
        ))
        LegalSection("٣. العمولة والدفع", listOf(
            "العمولة لكل طلب: الأعلى بين ${0.160.money} و ${(0.12 * 100).toInt()}٪ من قيمة الطلب.",
            "خصم الرتبة يقلّل نسبة العمولة حتى ٦٪ للرتبة البلاتينية.",
            "الصرف مرّتان أسبوعياً: **الأحد والأربعاء** عبر التحويل البنكي.",
            "في الدفع النقدي تُخصم العمولة من رصيدك لأنك قبضت المبلغ كاملاً.",
            "إذا نزل رصيدك تحت ${1.500.money} بالسالب، تتوقّف الطلبات حتى تسدّد.",
        ))
        LegalSection("٤. التزامات المندوب", listOf(
            "المحافظة على المنتجات وعدم فتحها.",
            "اللباس اللائق والتعامل باحترام مع العملاء.",
            "إبلاغ الدعم فوراً عن أي مشكلة في الطلب.",
            "يُمنع طلب بقشيش إجباري من العميل.",
        ))
        LegalSection("٥. الإيقاف والمسؤولية", listOf(
            "يحق لجاينك إيقاف الحساب عند: التأخير المتكرر، شكاوى العملاء، فقدان الطلبات، السلوك غير اللائق.",
            "المندوب مسؤول عن أي ضرر يسببه خلال التوصيل.",
            "الالتزام بقوانين المرور إلزامي، والحوادث مسؤولية المندوب وتأمينه الخاص.",
        ))
        LegalSection("٦. البيانات والموقع", listOf(
            "بتفعيل حالة «متصل» توافق على تتبّع موقعك لتنسيق الطلبات.",
            "يتوقّف التتبّع فوراً عند التحوّل إلى «غير متصل» أو إنهاء الطلب.",
            "مستنداتك (البطاقة، الرخصة، الملكية) للتحقّق فقط ولا تُعرض لأي عميل.",
        ))
    }
}

private fun openUri(context: Context, uri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

@Composable
private fun CommonClauses() {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), tint = MB.gold.copy(alpha = 0.12f)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End) {
            Text("بنود عامة", style = MBType.headline, color = MB.ink)
            KeyValueRow("القانون المطبَّق", "قوانين سلطنة عمان", isNumber = false)
            KeyValueRow("تعديل الشروط", "بإشعار ${AppConfig.termsChangeNoticeDays} أيام", isNumber = false)
            HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
            Text("تواصل الدعم", style = MBType.callout, color = MB.ink)
            Row(Modifier.clickableNoRipple { openUri(context, "mailto:${AppConfig.supportEmail}") },
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SFIcon("envelope.fill", size = 13.dp, tint = MB.green)
                Text(AppConfig.supportEmail, style = MBType.caption, color = MB.green)
            }
            Row(Modifier.clickableNoRipple {
                val digits = AppConfig.supportPhone.filter { it.isDigit() }
                openUri(context, "tel://$digits")
            }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SFIcon("phone.fill", size = 13.dp, tint = MB.green)
                Text(AppConfig.supportPhone, style = MBType.caption, color = MB.green)
            }
        }
    }
}

@Composable
private fun OpenOnWeb(url: String, title: String) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth()
            .glassSurface(radius = 16.dp, shadow = 0.06f)
            .clickableNoRipple {
                Feedback.impact(ImpactStyle.light)
                openUri(context, url)
            }
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SFIcon("safari.fill", size = 18.dp, tint = MB.green)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MBType.callout, color = MB.green)
            Text(url, style = MBType.num(10, androidx.compose.ui.text.font.FontWeight.Medium), color = MB.ink3)
        }
        SFIcon("arrow.up.left.square", size = 14.dp, tint = MB.green)
    }
}

@Composable
private fun Privacy() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OpenOnWeb(AppConfig.privacyPolicyURL, "افتح سياسة الخصوصية")
        Column(
            Modifier.fillMaxWidth().glassSurface(radius = 20.dp, shadow = 0.08f).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("سياسة الخصوصية", style = MBType.headline, color = MB.ink)
            Text("سياسة الخصوصية الكاملة منشورة على موقعنا وتُحدَّث هناك. " +
                "اضغط الزر فوق لتفتحها في المتصفّح.",
                style = MBType.caption, color = MB.ink2, modifier = Modifier.fillMaxWidth())
            HorizontalDivider(Modifier.alpha(0.25f), color = MB.hairline)
            Text("باختصار: نجمع اسمك ورقمك وموقعك لتوصيل طلبك فقط، " +
                "ولا نبيع بياناتك لأي جهة.",
                style = MBType.caption, color = MB.ink, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Refund() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OpenOnWeb(AppConfig.refundPolicyURL, "افتح سياسة الاسترجاع في المتصفّح")
        LegalSection("سياسة الاسترجاع والاسترداد", listOf("آخر تحديث: ٤ أكتوبر ٢٠٢٦."))
        LegalSection("١. الإلغاء", listOf(
            "تقدر تلغي طلبك قبل ما يقبله المندوب، ويرجع لك المبلغ كامل في حال استخدمت المحفظة."))
        LegalSection("٢. متى يحق لك استرجاع؟", listOf(
            "إذا الطلب التغى.", "أو وصل غلط.", "أو ما وصل نهائياً."))
        LegalSection("٣. كيف يرجع المبلغ؟", listOf(
            "يرجع على نفس طريقة الدفع — ثواني أو عن طريق التحويل البنكي.",
            "خلال ٣ إلى ٧ أيام عمل، وقد يوصل إلى ١٠ أيام."))
        LegalSection("٤. متى ما يرجع؟", listOf(
            "إذا الطلب وصل صحيح وكامل ما نقدر نرجع المبلغ."))
        LegalSection("٥. كيف تطلب استرجاع؟", listOf(
            "راسلنا على الواتساب أو على إيميل الشركة وأرسل رقم الطلب.",
            "تلقى رقم الطلب في «طلباتي» داخل التطبيق."))
    }
}

private val dataRows = listOf(
    Triple("رقم الهاتف", "للتحقّق من الهوية وتسجيل الدخول والتواصل حول الطلب", "phone.fill"),
    Triple("الموقع", "لتحديد نقاط الاستلام والتسليم وتتبّع الطلب أثناء تنفيذه", "location.fill"),
    Triple("سجل الطلبات", "لعرض طلباتك السابقة والمحاسبة وحلّ الخلافات", "list.bullet.rectangle"),
    Triple("مستندات المندوب", "للتحقّق من الرخصة والملكية والهوية قبل الاعتماد", "doc.text.fill"),
    Triple("المحادثات", "للتنسيق حول الطلب ومعالجة البلاغات", "bubble.left.and.bubble.right.fill"),
)

@Composable
private fun DataCollection() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ملخّص جمع البيانات", style = MBType.headline, color = MB.ink)
                dataRows.forEach { row ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.End) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            SFIcon(row.third, size = 13.dp, tint = MB.green)
                            Text(row.first, style = MBType.callout, color = MB.ink)
                            Spacer(Modifier.weight(1f))
                            MBChip("غير مرتبط بالتتبّع", tint = MB.green)
                        }
                        Text(row.second, style = MBType.micro, color = MB.ink2, textAlign = TextAlign.End)
                    }
                    if (row.first != dataRows.last().first)
                        HorizontalDivider(Modifier.alpha(0.3f), color = MB.hairline)
                }
            }
        }
        LegalSection("لا تتبّع إعلاني", listOf(
            "التطبيق لا يستخدم مُعرِّف الإعلانات ولا يشارك بياناتك مع شبكات إعلانية.",
            "لذلك لا يظهر لك طلب تتبّع (ATT).",
        ))
    }
}
