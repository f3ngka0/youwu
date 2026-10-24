package com.fengkao.timetable.data.xuexitong

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 学习通登录参数加密：AES-128-CBC + PKCS7，固定 key/iv，输出 Base64。
 * 与网页端 fanyalogin 的表单格式保持一致。
 */
object XuexitongCrypto {

    private val KEY = "u2oh6Vu^HWe4_AES".toByteArray(Charsets.UTF_8)

    fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(KEY, "AES"),
            IvParameterSpec(KEY),
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }
}
