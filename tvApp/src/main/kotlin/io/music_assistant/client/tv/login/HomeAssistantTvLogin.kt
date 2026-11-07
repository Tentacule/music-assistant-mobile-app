package io.music_assistant.client.tv.login

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Signs in with Home Assistant on the TV itself, without a browser. It does what the Home
 * Assistant login page does: the login flow API (`/auth/login_flow`) checks the username and the
 * password (then the two-factor code, when the user has one) and gives an authorization code. The
 * TV then opens the Music Assistant callback with that code, as the browser would, and reads the
 * access token from the page that Music Assistant answers.
 *
 * One instance is one sign-in, started from an authorization URL of `auth/authorization_url`.
 */
class HomeAssistantTvLogin(authorizationUrl: String) {
    private val log = Logger.withTag("HaTvLogin")
    private val authorize = URI(authorizationUrl)
    private val query = parseQuery(authorize.rawQuery.orEmpty())
    private val haBase = "${authorize.scheme}://${authorize.rawAuthority}"
    private val clientId = query["client_id"].orEmpty()
    private val redirectUri = query["redirect_uri"].orEmpty()
    private val state = query["state"].orEmpty()
    private var flowId: String? = null

    /** What the sign-in needs next. */
    sealed interface Step {
        /** The username and the password. [error] is set after a refusal. */
        data class Credentials(val error: Error? = null) : Step

        /** The two-factor code of the user. */
        data class Code(val error: Error? = null) : Step

        /** The sign-in is complete: the Music Assistant access token. */
        data class Done(val token: String) : Step

        data class Failed(val message: String?) : Step
    }

    enum class Error { INVALID_CREDENTIALS, INVALID_CODE, OTHER }

    suspend fun start(): Step = io {
        if (clientId.isEmpty() || redirectUri.isEmpty() || state.isEmpty()) {
            return@io Step.Failed("Unexpected authorization URL")
        }
        val answer = post(
            "$haBase/auth/login_flow",
            JSONObject()
                .put("client_id", clientId)
                .put("handler", JSONArray().put("homeassistant").put(JSONObject.NULL))
                .put("redirect_uri", redirectUri),
        )
        flowId = answer.optString("flow_id").takeIf { it.isNotBlank() }
        next(answer)
    }

    suspend fun submitCredentials(username: String, password: String): Step =
        submit(JSONObject().put("username", username).put("password", password))

    suspend fun submitCode(code: String): Step = submit(JSONObject().put("code", code.trim()))

    private suspend fun submit(fields: JSONObject): Step = io {
        val id = flowId ?: return@io Step.Failed("No sign-in in progress")
        next(post("$haBase/auth/login_flow/$id", fields.put("client_id", clientId)))
    }

    private fun next(answer: JSONObject): Step {
        when (answer.optString("type")) {
            "create_entry" -> {
                val code = answer.optString("result").takeIf { it.isNotBlank() }
                    ?: return Step.Failed("No authorization code")
                return finish(code)
            }
            "form" -> {
                val error = answer.optJSONObject("errors")?.optString("base")?.takeIf { it.isNotBlank() }
                val names = answer.optJSONArray("data_schema")?.let { schema ->
                    (0 until schema.length()).mapNotNull { schema.optJSONObject(it)?.optString("name") }
                }.orEmpty()
                return when {
                    "code" in names -> Step.Code(error?.let { if (it == "invalid_code") Error.INVALID_CODE else Error.OTHER })
                    "username" in names -> Step.Credentials(
                        error?.let { if (it == "invalid_auth") Error.INVALID_CREDENTIALS else Error.OTHER },
                    )
                    else -> Step.Failed("Unsupported sign-in step: ${answer.optString("step_id")}")
                }
            }
            else -> return Step.Failed(answer.optString("reason").takeIf { it.isNotBlank() } ?: answer.optString("message"))
        }
    }

    /** Opens the Music Assistant callback with the code, as the browser would, for the token. */
    private fun finish(code: String): Step {
        val separator = if ('?' in redirectUri) '&' else '?'
        val url = "$redirectUri${separator}code=${code.encode()}&state=${state.encode()}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        return try {
            val body = (if (connection.responseCode < 400) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            TOKEN_IN_PAGE.find(body)?.groupValues?.get(1)?.let { Step.Done(it) }
                ?: connection.getHeaderField("Location")?.let { parseQuery(URI(it).rawQuery.orEmpty())["code"] }
                    ?.let { Step.Done(it) }
                ?: Step.Failed(FAILURE_IN_PAGE.find(body)?.groupValues?.get(1))
        } finally {
            connection.disconnect()
        }
    }

    private fun post(url: String, body: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            runCatching { JSONObject(text) }.getOrElse { JSONObject().put("message", "HTTP ${connection.responseCode}") }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun io(block: suspend () -> Step): Step = withContext(Dispatchers.IO) {
        runCatching { block() }.getOrElse {
            log.w(it) { "Home Assistant sign-in failed" }
            Step.Failed(it.message)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        val TOKEN_IN_PAGE = Regex("""const token\s*=\s*["']([^"']+)["']""")
        val FAILURE_IN_PAGE = Regex("""<p>([^<]+)</p>""")
    }
}

private fun String.encode(): String = URLEncoder.encode(this, "UTF-8")

private fun parseQuery(raw: String): Map<String, String> = raw.split('&')
    .filter { it.isNotEmpty() }
    .associate { pair ->
        URLDecoder.decode(pair.substringBefore('='), "UTF-8") to
            URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
    }
