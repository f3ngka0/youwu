package com.fengkao.timetable.data.xuexitong

import org.junit.Assert.assertEquals
import org.junit.Test

class XuexitongCryptoTest {

    @Test
    fun encryptMatchesWebLoginCipherText() {
        // openssl 用固定 key/iv 计算出的已知结果，防止误改 padding 或 key。
        assertEquals("yYcVatS+4J+JGnm88UM56A==", XuexitongCrypto.encrypt("13800138000"))
    }

    @Test
    fun encryptIsDeterministicForSameInput() {
        assertEquals(XuexitongCrypto.encrypt("test1234"), XuexitongCrypto.encrypt("test1234"))
    }
}
