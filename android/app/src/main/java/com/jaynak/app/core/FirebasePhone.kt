package com.jaynak.app.core

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** تحقّق الهاتف عبر Firebase Authentication — يحلّ محلّ Twilio. */
internal object FirebasePhone {

    private const val PROJECT_ID = "jayanik"
    // نسخة التجربة (debug) ونسخة المتجر (release) لكلٍّ منهما تطبيق Firebase خاص
    private val APP_ID = if (com.jaynak.app.BuildConfig.DEBUG)
        "1:802843878111:android:44e460ddcc879999543f6a"
    else "1:802843878111:android:c0797439cc1d721c543f6a"
    private const val API_KEY = "AIzaSyAS1oUQVnpyl7_tBZglFrlvwH56hYFV-Hc"

    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var instant: PhoneAuthCredential? = null

    private fun auth(): FirebaseAuth {
        val existing = FirebaseApp.getApps(Boot.app).firstOrNull()
        val app = existing ?: FirebaseApp.initializeApp(
            Boot.app,
            FirebaseOptions.Builder()
                .setApplicationId(APP_ID)
                .setApiKey(API_KEY)
                .setProjectId(PROJECT_ID)
                .build(),
        )
        return FirebaseAuth.getInstance(app!!)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { if (c.isActive) c.resume(it) }
        addOnFailureListener { if (c.isActive) c.resumeWithException(it) }
    }

    private fun map(e: Throwable): OTPService.Failure = when (e) {
        is OTPService.Failure -> e
        is FirebaseTooManyRequestsException -> {
            SendLock.lock()
            OTPService.Failure.RateLimited("محاولات كثيرة — يُفتح الإرسال بعد ${SendLock.format(SendLock.remainingSec())}")
        }
        is FirebaseAuthInvalidCredentialsException ->
            if (e.errorCode == "ERROR_SESSION_EXPIRED") OTPService.Failure.Expired
            else if (e.errorCode == "ERROR_INVALID_PHONE_NUMBER") OTPService.Failure.InvalidPhone
            else { SendLock.onWrongCode(); OTPService.Failure.WrongCode(null) }
        is FirebaseException ->
            OTPService.Failure.SendFailed(e.message ?: "تعذّر إرسال الرمز")
        else -> OTPService.Failure.Unreachable(e.message ?: "تعذّر الاتصال")
    }

    /** يرسل الرمز ويعيد verificationId. */
    suspend fun send(phone: String): String = try {
        SendLock.beforeSend()
        suspendCancellableCoroutine<String> { cont ->
            val act = Boot.activity
            if (act == null) {
                cont.resumeWithException(OTPService.Failure.SendFailed("افتح التطبيق وأعد المحاولة"))
                return@suspendCancellableCoroutine
            }
            val auth = auth()
            auth.setLanguageCode("ar")
            val cb = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(c: PhoneAuthCredential) {
                    instant = c
                    if (cont.isActive) cont.resume("INSTANT")
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (cont.isActive) cont.resumeWithException(map(e))
                }

                override fun onCodeSent(id: String, t: PhoneAuthProvider.ForceResendingToken) {
                    resendToken = t
                    instant = null
                    if (cont.isActive) cont.resume(id)
                }
            }
            val b = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(AppConfig.countryDialCode + phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(act)
                .setCallbacks(cb)
            resendToken?.let { b.setForceResendingToken(it) }
            PhoneAuthProvider.verifyPhoneNumber(b.build())
        }
    } catch (e: Throwable) {
        throw map(e)
    }

    /** يتحقق من الرمز ويعيد Firebase ID token. */
    suspend fun verify(code: String, verificationId: String): String = try {
        val cred = if (verificationId == "INSTANT" && instant != null) instant!!
        else PhoneAuthProvider.getCredential(verificationId, code)
        val auth = auth()
        val user = auth.signInWithCredential(cred).await().user
            ?: throw OTPService.Failure.WrongCode(null)
        val token = user.getIdToken(true).await().token
            ?: throw OTPService.Failure.Unreachable("تعذّر إكمال التحقق")
        auth.signOut()
        SendLock.clear()
        token
    } catch (e: Throwable) {
        throw map(e)
    }
}
