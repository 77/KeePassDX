package com.kunzisoft.keepass.otp

import com.kunzisoft.keepass.model.OtpModel
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.binary.Hex
import java.nio.charset.Charset
import java.util.*

data class OtpElement(var otpModel: OtpModel = OtpModel()) {

    var type
        get() = otpModel.type
        set(value) {
            otpModel.type = value
            if (type == OtpType.HOTP) {
                if (!OtpTokenType.getHotpTokenTypeValues().contains(tokenType))
                    tokenType = OtpTokenType.RFC4226
            }
            if (type == OtpType.TOTP) {
                if (!OtpTokenType.getTotpTokenTypeValues().contains(tokenType))
                    tokenType = OtpTokenType.RFC6238
            }
        }

    var tokenType
        get() = otpModel.tokenType
        set(value) {
            otpModel.tokenType = value
            when (tokenType) {
                OtpTokenType.RFC4226 -> {
                    otpModel.algorithm = TokenCalculator.DEFAULT_ALGORITHM
                    otpModel.digits = TokenCalculator.OTP_DEFAULT_DIGITS
                    otpModel.counter = TokenCalculator.HOTP_INITIAL_COUNTER
                }
                OtpTokenType.RFC6238 -> {
                    otpModel.algorithm = TokenCalculator.DEFAULT_ALGORITHM
                    otpModel.digits = TokenCalculator.OTP_DEFAULT_DIGITS
                    otpModel.period = TokenCalculator.TOTP_DEFAULT_PERIOD
                }
                OtpTokenType.STEAM -> {
                    otpModel.algorithm = TokenCalculator.DEFAULT_ALGORITHM
                    otpModel.digits = TokenCalculator.STEAM_DEFAULT_DIGITS
                    otpModel.period = TokenCalculator.TOTP_DEFAULT_PERIOD
                }
            }
        }

    var name
        get() = otpModel.name
        set(value) {
            otpModel.name = value
        }

    var issuer
        get() = otpModel.issuer
        set(value) {
            otpModel.issuer = value
        }

    var secret
        get() = otpModel.secret
        set(value) {
            otpModel.secret = value
        }

    var counter
        get() = otpModel.counter
        set(value) {
            otpModel.counter = if (value < 0) TokenCalculator.HOTP_INITIAL_COUNTER else value
        }

    var period
        get() = otpModel.period
        set(value) {
            otpModel.period = if (value <= 0 || value > 60) TokenCalculator.TOTP_DEFAULT_PERIOD else value
        }

    var digits
        get() = otpModel.digits
        set(value) {
            otpModel.digits = if (value <= 0) TokenCalculator.OTP_DEFAULT_DIGITS else value
        }

    var algorithm
        get() = otpModel.algorithm
        set(value) {
            otpModel.algorithm = value
        }

    fun setUTF8Secret(secret: String) {
        otpModel.secret = secret.toByteArray(Charset.forName("UTF-8"))
    }

    fun setHexSecret(secret: String) {
        try {
            otpModel.secret = Hex.decodeHex(secret)
        } catch (e: DecoderException) {
            e.printStackTrace()
        }
    }

    fun getBase32Secret(): String {
        return Base32().encodeAsString(otpModel.secret)
    }

    fun setBase32Secret(secret: String) {
        otpModel.secret = Base32().decode(secret.toByteArray())
    }

    fun setBase64Secret(secret: String) {
        otpModel.secret = Base64().decode(secret.toByteArray())
    }

    val token: String
        get() {
            return when (type) {
                OtpType.HOTP -> TokenCalculator.HOTP(secret, counter.toLong(), digits, algorithm)
                OtpType.TOTP -> when (tokenType) {
                    OtpTokenType.STEAM -> TokenCalculator.TOTP_Steam(secret, period, digits, algorithm)
                    else -> TokenCalculator.TOTP_RFC6238(secret, period, digits, algorithm)
                }
            }
        }

    val secondsRemaining: Int
        get() = otpModel.period - (System.currentTimeMillis() / 1000 % otpModel.period).toInt()

    fun shouldRefreshToken(): Boolean {
        return secondsRemaining == otpModel.period
    }
}

enum class OtpType {
    HOTP,   // counter based
    TOTP;    // time based
}

enum class OtpTokenType {
    RFC4226,    // HOTP
    RFC6238,    // TOTP

    // Proprietary
    STEAM;    // TOTP Steam

    companion object {
        fun getFromString(tokenType: String): OtpTokenType {
            return when (tokenType.toLowerCase(Locale.ENGLISH)) {
                "s", "steam" -> STEAM
                else -> RFC6238
            }
        }

        fun getTotpTokenTypeValues(getProprietaryElements: Boolean = true): Array<OtpTokenType> {
            return if (getProprietaryElements)
                arrayOf(RFC6238, STEAM)
            else
                arrayOf(RFC6238)
        }

        fun getHotpTokenTypeValues(): Array<OtpTokenType> {
            return  arrayOf(RFC4226)
        }
    }
}