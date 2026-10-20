package com.fengkao.timetable.data.shower

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.URLDecoder

class ShowerApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ShowerApi
    private val auth = ShowerAuth("session", "123", "456", "789", "13800138000")
    @Before fun setup() { server = MockWebServer(); server.start(); api = ShowerApi(server.url("/").toString()) }
    @After fun teardown() { server.shutdown() }
    private fun reply(json: String) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(json)) }
    private fun fields(body: String) = body.split('&').associate {
        val pair = it.split('=', limit = 2); URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
    }
    private val loginJson = """{"success":true,"data":{"loginCode":"abc","userId":123,"userAccount":{"accountId":456,"projectId":789}}}"""
    @Test fun bluetoothRateUsesDeviceStateAndReturnsServerPayload() {
        reply("""{"success":true,"data":{"downData":"802100","consumeDate":"202610071234","orderNo":"A1"}}""")
        val device = ShowerDevice("S1", "浴室", "001122334455", true, 123, 4, 1, "AABBCCDDEEFF")
        val state = com.fengkao.timetable.data.shower.ble.DeviceQueryState("20", "A1B2C3D4", 0, 0x20, 0x01)
        val result = api.bleRate(auth, device, state)
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x21, 0), result.data)
        assertEquals("202610071234", result.consumeDate)
        val request = server.takeRequest()
        assertEquals("/order/downRate/bluetooth/rateOrder", request.path)
        val body = fields(request.body.readUtf8())
        assertEquals("AABBCCDDEEFF", body["macAddress"])
        assertEquals("a1b2c3d4", body["randomNumber"])
        assertEquals("2001", body["macType"])
        assertEquals("fbfc09eda7024f753917e058194626be", body["signature"])
    }

    @Test fun bluetoothUploadUsesMatchingLowercasePayloadAndSignature() {
        reply("""{"success":false,"errorCode":206,"data":{"clData":"abc"}}""")
        val state = com.fengkao.timetable.data.shower.ble.DeviceQueryState("20", "A1B2C3D4", 3, 0x20, 0x01)
        val payload = ByteArray(42).apply { this[0] = 0x80.toByte(); this[1] = 0xAB.toByte(); this[2] = 0xCD.toByte() }
        val expectedHex = "80abcd" + "00".repeat(39)
        assertEquals("abc", api.bleUpload(auth, state, payload).clData)
        val request = server.takeRequest()
        assertEquals("/order/upload/bluetooth/data", request.path)
        val body = fields(request.body.readUtf8())
        assertEquals(expectedHex, body["xfData"])
        assertEquals("a1b2c3d4", body["randomNumber"])
        assertEquals(com.fengkao.timetable.data.shower.ble.BleSignBuilder.signUpload(auth.loginCode, auth.phone, expectedHex), body["signature"])
    }
    @Test fun invalidConsumptionResponsesNeverReachBillingEndpoint() {
        val state = com.fengkao.timetable.data.shower.ble.DeviceQueryState("", "0000", 3, 0, 1)
        for (payload in listOf(ByteArray(0), byteArrayOf(0x80.toByte()), ByteArray(41) { 0x80.toByte() }, ByteArray(42))) {
            try {
                api.bleUpload(auth, state, payload)
                fail("Invalid consumption response was accepted")
            } catch (_: IOException) { }
        }
        assertEquals(0, server.requestCount)
    }
    @Test fun campusBalanceHasPriorityAndUsesPhoneAlias() {
        reply("""{"success":true,"data":{"amount":"16.76","signStatus":1}}""")
        assertEquals(ShowerBalance("16.76", "一卡通余额"), api.balance(auth))
        val request = server.takeRequest()
        assertEquals("/settlement/campus/userInfo", request.requestUrl!!.encodedPath)
        assertEquals(auth.phone, request.requestUrl!!.queryParameter("telPhone"))
    }
    @Test fun unsignedCampusFallsBackToRealWallet() {
        reply("""{"success":true,"data":{"signStatus":0}}""")
        reply("""{"success":true,"data":{"money":"0"}}""")
        assertEquals(ShowerBalance("0.00", "趣智校园钱包余额"), api.balance(auth))
        server.takeRequest()
        assertEquals("/account/wallet", server.takeRequest().requestUrl!!.encodedPath)
    }
    @Test fun campusFailureCanFallBackToWallet() {
        reply("""{"success":false,"errorMessage":"未签约"}""")
        reply("""{"success":true,"data":{"money":"5.50"}}""")
        assertEquals("5.50", api.balance(auth).amount)
    }
    @Test(expected = IOException::class) fun missingBalanceIsNotShownAsZero() {
        reply("""{"success":true,"data":null}""")
        reply("""{"success":true,"data":{"money":"null"}}""")
        api.balance(auth)
    }
    @Test(expected = ShowerSessionExpired::class) fun expiredBalanceDoesNotFallBack() {
        reply("""{"success":false,"errorMessage":"登录失效"}""")
        api.balance(auth)
    }
    @Test fun smsUsesDynamicPhoneSignature() {
        reply("""{"success":true}""")
        api.sendSms(auth.phone)
        val request = server.takeRequest()
        assertEquals("/user/verification/code/get", request.requestUrl!!.encodedPath)
        assertEquals("f000554d9cb90c44bd0ae2ded9a847aa", request.requestUrl!!.queryParameter("secret"))
        assertEquals("3", request.requestUrl!!.queryParameter("typeId"))
        assertNull(request.requestUrl!!.queryParameter("loginCode"))
    }
    @Test fun smsLoginParsesNumericAccountIds() {
        reply(loginJson)
        val account = api.login(auth.phone, "123456", true)
        assertEquals("789", account.projectId)
        val request = server.takeRequest()
        assertEquals("/user/registerAndLogin", request.path)
        assertEquals("5", fields(request.body.readUtf8())["type"])
    }
    @Test fun passwordUsesOfficialHashAndIsNotSentRaw() {
        reply(loginJson)
        api.login(auth.phone, "mypassword", false)
        val request = server.takeRequest()
        assertEquals("/user/login", request.path)
        val body = fields(request.body.readUtf8())
        assertEquals("4BC85B3E44", body["password"])
        assertEquals("6.5.28", body["version"])
        assertEquals("0", body["type"])
    }
    @Test(expected = IllegalArgumentException::class) fun incompleteAccountCannotBeSaved() {
        reply("""{"success":true,"data":{"loginCode":"abc","userId":123,"userAccount":{"accountId":0,"projectId":0}}}""")
        api.login(auth.phone, "123456", true)
    }
    @Test fun deviceQueryIncludesAuthAndProjectHeaders() {
        reply("""{"success":true,"data":{"snCode":"SN1","deviceName":"1栋301浴室","macAddress":"AABBCCDDEEFF","smallTypeId":1}}""")
        val device = api.device(auth, "AABBCCDDEEFF")
        assertFalse(device.bluetoothOnly)
        assertEquals("SN1", device.sn)
        val request = server.takeRequest()
        assertEquals("session", request.requestUrl!!.queryParameter("loginCode"))
        assertEquals(auth.phone, request.requestUrl!!.queryParameter("telPhone"))
        assertEquals("789", request.getHeader("Config-Project"))
    }
    @Test fun occupiedDeviceWithNoOwnerFlagCannotBeStoppedAsOwnOrder() {
        reply("""{"success":false,"errorCode":307,"data":{"orderNo":"42"}}""")
        val order = api.query(auth, "SN1")
        assertTrue(order.active); assertFalse(order.owner)
    }
    @Test fun ownOrderIsRestored() {
        reply("""{"success":true,"data":{"orderNo":"42","isOwner":true}}""")
        val order = api.query(auth, "SN1")
        assertTrue(order.active); assertTrue(order.owner); assertEquals("42", order.number)
    }
    @Test fun nullOrderIsIdle() {
        reply("""{"success":true,"data":{"orderNo":null,"isOwner":true}}""")
        assertFalse(api.query(auth, "SN1").active)
    }
    @Test fun openCommandIncludesBothPhoneFieldsAndMode() {
        reply("""{"success":true}"""); api.start(auth, "SN1")
        val request = server.takeRequest()
        assertEquals("/order/tcpDevice/downRate/rateOrder", request.path)
        val fields = fields(request.body.readUtf8())
        assertEquals(auth.phone, fields["telephone"]); assertEquals(auth.phone, fields["telPhone"])
        assertEquals("0", fields["xfModel"]); assertEquals("SN1", fields["snCode"])
        assertEquals(1, server.requestCount)
    }
    @Test fun acceptedCommandDoesNotImplyValveOpened() {
        reply("""{"success":true,"data":null}""")
        assertNull(api.opened(auth, "SN1"))
    }
    @Test fun resultConfirmsOpenAndReturnsOrderNumber() {
        reply("""{"success":true,"data":{"state":1,"result":0,"orderNo":"42"}}""")
        assertEquals("42", api.opened(auth, "SN1"))
    }
    @Test fun stopUsesActualOrderNumber() {
        reply("""{"success":true}"""); api.stop(auth, "SN1", "42")
        val request = server.takeRequest()
        assertEquals("/order/tcpDevice/closeOrder", request.path)
        assertEquals("42", fields(request.body.readUtf8())["orderNo"])
    }
    @Test(expected = ShowerSessionExpired::class) fun expiredSessionRequestsLogin() {
        reply("""{"success":false,"errorCode":21,"errorMessage":"登录已失效"}""")
        api.validate(auth)
    }
    @Test fun failedStartIsNeverRetried() {
        reply("""{"success":false,"errorCode":306,"errorMessage":"设备不在线"}""")
        try { api.start(auth, "SN1"); fail("should fail") } catch (e: IOException) { assertEquals("设备不在线", e.message) }
        assertEquals(1, server.requestCount)
    }
    @Test fun qrCodeParsesOfficialFormatAndRejectsUnrelatedInputs() {
        assertEquals("AABBCCDDEEFF", ShowerApi.deviceCode("KLCXKJ-Water,AA:BB:CC:DD:EE:FF"))
        assertEquals("AABBCCDDEEFF", ShowerApi.deviceCode("aabbccddeeff"))
        assertNull(ShowerApi.deviceCode("https://example.com"))
        assertNull(ShowerApi.deviceCode("13800138000"))
    }
}
