package io.music_assistant.client.tv.login

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URLEncoder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A fake Home Assistant login flow and Music Assistant callback, on one local server. */
class HomeAssistantTvLoginTest {
    private lateinit var server: HttpServer
    private var callbackQuery: String? = null
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/auth/login_flow") { exchange ->
            val body = JSONObject(exchange.requestBody.bufferedReader().readText())
            val answer = when {
                exchange.requestURI.path == "/auth/login_flow" -> form("username", "password")
                body.has("code") && body.getString("code") == "123456" ->
                    JSONObject().put("type", "create_entry").put("result", "auth-code")
                body.has("code") -> form("code", error = "invalid_code")
                body.optString("password") == "good" -> form("code")
                else -> form("username", "password", error = "invalid_auth")
            }
            exchange.reply(answer.toString())
        }
        server.createContext("/auth/callback") { exchange ->
            callbackQuery = exchange.requestURI.rawQuery
            exchange.reply("<script>const token = \"jwt.token.value\";\nconst redirectUrl = \"/\";</script>")
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun login(): HomeAssistantTvLogin {
        val redirect = URLEncoder.encode("$base/auth/callback?provider_id=homeassistant", "UTF-8")
        return HomeAssistantTvLogin("$base/auth/authorize?client_id=$base&redirect_uri=$redirect&state=the-state")
    }

    @Test
    fun `a wrong password asks again with the error`() = runTest {
        val login = login()
        assertEquals(HomeAssistantTvLogin.Step.Credentials(), login.start())
        assertEquals(
            HomeAssistantTvLogin.Step.Credentials(HomeAssistantTvLogin.Error.INVALID_CREDENTIALS),
            login.submitCredentials("alice", "bad"),
        )
    }

    @Test
    fun `the two-factor code completes the sign-in through the Music Assistant callback`() = runTest {
        val login = login()
        login.start()
        assertEquals(HomeAssistantTvLogin.Step.Code(), login.submitCredentials("alice", "good"))
        assertEquals(HomeAssistantTvLogin.Step.Code(HomeAssistantTvLogin.Error.INVALID_CODE), login.submitCode("000000"))
        assertEquals(HomeAssistantTvLogin.Step.Done("jwt.token.value"), login.submitCode("123456"))
        assertEquals("provider_id=homeassistant&code=auth-code&state=the-state", callbackQuery)
    }

    private fun form(vararg fields: String, error: String? = null) = JSONObject()
        .put("type", "form")
        .put("flow_id", "flow-1")
        .put("data_schema", fields.map { JSONObject().put("name", it) })
        .put("errors", JSONObject().apply { error?.let { put("base", it) } })

    private fun HttpExchange.reply(text: String) {
        val bytes = text.toByteArray()
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
