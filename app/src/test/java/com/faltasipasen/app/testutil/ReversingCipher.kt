package com.faltasipasen.app.testutil

import com.faltasipasen.app.data.StringCipher

/** Cifrado falso y reversible para tests: nunca guarda el texto tal cual. */
class ReversingCipher : StringCipher {
    override fun encrypt(plain: String): String = "enc:" + plain.reversed()
    override fun decrypt(encoded: String): String? =
        if (encoded.startsWith("enc:")) encoded.removePrefix("enc:").reversed() else null
}
