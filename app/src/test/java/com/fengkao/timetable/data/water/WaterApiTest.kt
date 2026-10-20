package com.fengkao.timetable.data.water

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class WaterApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: WaterApi
    @Before fun setup() {
        server = MockWebServer(); server.start()
        api = WaterApi(server.url("/api/v1").toString(), "test-client")
    }
    @After fun tearDown() { server.shutdown() }
    private fun reply(body: String) { server.enqueue(MockResponse().setBody(body)) }

    @Test fun smsUsesGraphCodeAndAccountHeaders() {
        reply("""{"code":0}""")
        api.sendSms("13800000000", "ABCD", "captcha-key")
        val req = server.takeRequest()
        assertEquals("/api/v1/acc/login/code", req.path)
        assertEquals("1,5", req.getHeader("ApplicationType"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("ABCD", body.getString("authCode"))
        assertEquals("captcha-key", body.getString("s"))
    }
    @Test fun loginUsesDevicePlatformAndReturnsAppToken() {
        reply("""{"code":0,"data":{"al":{"token":"device-token"}}}""")
        assertEquals("device-token", api.login("13800000000", "123456"))
        val req = server.takeRequest()
        assertEquals("1,1", req.getHeader("ApplicationType"))
        assertEquals("3.1.9", req.getHeader("VersionCode"))
        assertEquals("test-client", JSONObject(req.body.readUtf8()).getString("cid"))
    }
    @Test fun missingLoginTokenIsRejected() {
        reply("""{"code":0,"data":{}}""")
        assertThrows(IOException::class.java) { api.login("13800000000", "123456") }
    }
    @Test fun favoritesAreDeduplicatedAndUnnamedDeviceHasFallback() {
        reply("""{"code":0,"data":{"account":{"id":"1"},"favos":[{"id":"12345678","name":"宿舍一楼"},{"id":"12345678"},{"id":"87654321"},{}]}}""")
        val devices = api.devices("token")
        assertEquals(listOf(WaterDevice("12345678", "宿舍一楼"), WaterDevice("87654321", "饮水设备")), devices)
        assertEquals("token", server.takeRequest().getHeader("Authorization"))
    }
    @Test fun missingAccountMeansExpiredLoginInsteadOfEmptyList() {
        // 服务端凭据失效时设备首页仍返回 code=0，但 data 内没有 account。
        reply("""{"code":0,"data":{"ads":[]}}""")
        assertThrows(WaterSessionExpired::class.java) { api.devices("expired") }
    }
    @Test fun startUsesOfficialClientHeadersAndParameters() {
        reply("""{"code":0}""")
        api.start("token", "12345678")
        val req = server.takeRequest()
        assertEquals("/api/v1/dev/start?did=12345678&upgrade=true&ptype=91&args=&rcp=false&cnt=1", req.path)
        assertEquals("1,1", req.getHeader("ApplicationType"))
        assertEquals("token", req.getHeader("Authorization"))
        assertEquals("3.1.9", req.getHeader("VersionCode"))
        assertEquals("Android_ilife798_3.1.9", req.getHeader("User-Agent"))
        assertEquals(1, server.requestCount)
    }
    @Test fun startCanFallBackToAlipayPaymentType() {
        reply("""{"code":0}""")
        api.start("token", "12345678", ptype = 21)
        assertEquals("/api/v1/dev/start?did=12345678&upgrade=true&ptype=21&args=&rcp=false&cnt=1", server.takeRequest().path)
    }
    @Test fun deviceStatusMapsLinkAndBusinessCodes() {
        reply("""{"code":0,"data":{"device":{"status":1,"gene":{"status":99}}}}""")
        assertTrue(api.status("token", "12345678").available)
        assertEquals("token", server.takeRequest().getHeader("Authorization"))
        reply("""{"code":0,"data":{"device":{"status":0,"gene":{"status":99}}}}""")
        assertTrue(api.status("token", "12345678").offline)
        reply("""{"code":0,"data":{"device":{"status":1,"gene":{"status":98}}}}""")
        assertTrue(api.status("token", "12345678").disabled)
        reply("""{"code":0,"data":{"device":{"status":1,"gene":{"status":1}}}}""")
        assertFalse(api.status("token", "12345678").available)
    }
    @Test fun bindFavoritesDeviceWithoutStartingIt() {
        reply("""{"code":0}""")
        api.bind("token", "12345678")
        assertEquals("/api/v1/dev/favo?did=12345678&remove=0", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }
    @Test fun qrCodeIsResolvedToRealDeviceIdAndFallsBackToScannedCode() {
        reply("""{"code":0,"data":{"dev":{"id":"76543210"}}}""")
        assertEquals("76543210", api.resolveQrDevice("token", "scan-ticket-01"))
        assertEquals("/api/v1/qr/use?id=scan-ticket-01", server.takeRequest().path)
        reply("""{"code":-1,"msg":"二维码已失效"}""")
        assertEquals("12345678", api.resolveQrDevice("token", "12345678"))
    }
    @Test fun expiredSessionIsRecognized() {
        reply("""{"code":-99,"msg":"expired"}""")
        assertThrows(WaterSessionExpired::class.java) { api.devices("expired") }
    }
    @Test fun insufficientBalanceDoesNotReportSuccessOrRetry() {
        reply("""{"code":-52}""")
        val error = assertThrows(IOException::class.java) { api.start("token", "12345678") }
        assertTrue(error.message.orEmpty().contains("欠费"))
        assertEquals(1, server.requestCount)
    }
    @Test fun unrelatedWebUrlIsNotADevice() {
        assertEquals("https://example.com/help", DeviceIdParser.normalize("https://example.com/help"))
        assertEquals("12345678", DeviceIdParser.normalize("https://example.com/?did=12345678"))
        assertEquals("12345678", DeviceIdParser.normalize("https://www.hnkzy.com/q/12345678"))
        assertEquals("12345678", DeviceIdParser.normalize("{\"deviceId\":\"12345678\"}"))
        assertEquals("", DeviceIdParser.normalize("  "))
    }
}
