package io.music_assistant.client.tv.login

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.SecureRandom

/** The texts of the page that the phone opens, in the language of the TV. */
data class LoginPageTexts(
    val title: String,
    val intro: String,
    val username: String,
    val password: String,
    val signIn: String,
    val or: String,
    val homeAssistant: String,
    val success: String,
    val failure: String,
    val expired: String,
)

/**
 * A small web page on the TV, for signing in from a phone: the TV shows its address as a QR code,
 * the phone opens it and types the credentials with its own keyboard, or signs in with Home
 * Assistant. Home Assistant sends the browser back to `/ha/done` here, through the Music Assistant
 * callback page, with the access token as `code`.
 *
 * Every request must carry [secret] (`k`): another device of the network cannot use the page
 * without the QR code. The page runs only while the TV shows its sign-in screen.
 */
class TvLoginWebServer(
    private val texts: LoginPageTexts,
    private val serverName: String,
    private val homeAssistant: () -> Boolean,
    private val onCredentials: suspend (username: String, password: String) -> String?,
    private val homeAssistantUrl: suspend (returnUrl: String) -> String?,
    private val onHomeAssistantToken: (String) -> Unit,
) {
    private val log = Logger.withTag("TvLoginWeb")
    private val scope = CoroutineScope(Dispatchers.IO)
    private var socket: ServerSocket? = null

    val secret: String = ByteArray(SECRET_BYTES).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    /** Starts the page and returns its port, or null when no port is free. */
    fun start(): Int? {
        val server = PORTS.firstNotNullOfOrNull { port -> runCatching { ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress(port)) } }.getOrNull() }
            ?: return null
        socket = server
        scope.launch {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                }
                scope.launch { client.use { handle(it) } }
            }
        }
        log.i { "Sign-in page on port ${server.localPort}" }
        return server.localPort
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = input.readLine() ?: return
        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }
            .mapNotNull { line -> line.split(":", limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key.trim().lowercase() to value.trim() }
        val (method, target) = requestLine.split(" ").let { it.getOrNull(0) to it.getOrNull(1) }
        val path = target?.substringBefore('?').orEmpty()
        val query = parseForm(target?.substringAfter('?', "").orEmpty())
        val body = headers["content-length"]?.toIntOrNull()?.coerceAtMost(MAX_BODY)?.let { length ->
            val bytes = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(bytes, read, length - read)
                if (n < 0) break
                read += n
            }
            parseForm(String(bytes, 0, read, Charsets.UTF_8))
        }.orEmpty()
        val out = client.getOutputStream()
        val key = query["k"] ?: body["k"]
        if (key != secret) {
            out.respond(403, page(texts.expired))
            return
        }
        when {
            method == "GET" && path == "/" -> out.respond(200, form(error = null))
            method == "POST" && path == "/login" -> {
                val username = body["username"].orEmpty().trim()
                val password = body["password"].orEmpty()
                val error = runBlocking { onCredentials(username, password) }
                out.respond(200, error?.let { form(it) } ?: page(texts.success))
            }

            method == "GET" && path == "/ha" && homeAssistant() -> {
                val host = headers["host"] ?: return out.respond(400, page(texts.failure))
                val url = runBlocking { homeAssistantUrl("http://$host/ha/done?k=$secret") }
                url?.let { out.redirect(it) } ?: out.respond(200, page(texts.failure))
            }

            method == "GET" && path == "/ha/done" -> {
                val token = query["code"]
                if (token.isNullOrBlank()) {
                    out.respond(200, page(texts.failure))
                } else {
                    onHomeAssistantToken(token)
                    out.respond(200, page(texts.success))
                }
            }

            else -> out.respond(404, page(texts.failure))
        }
    }

    private fun form(error: String?) = page(
        """
        <p>${texts.intro.html()}</p>
        ${error?.let { "<p class=\"error\">${it.html()}</p>" }.orEmpty()}
        <form method="post" action="/login">
          <input type="hidden" name="k" value="$secret">
          <label>${texts.username.html()}<input name="username" autocomplete="username" autocapitalize="none" required></label>
          <label>${texts.password.html()}<input name="password" type="password" autocomplete="current-password" required></label>
          <button type="submit">${texts.signIn.html()}</button>
        </form>
        ${
            if (homeAssistant()) {
                "<p class=\"or\">${texts.or.html()}</p><a class=\"button ha\" href=\"/ha?k=$secret\">${texts.homeAssistant.html()}</a>"
            } else {
                ""
            }
        }
        """.trimIndent(),
        raw = true,
    )

    private fun page(content: String, raw: Boolean = false) = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>${texts.title.html()}</title>
        <style>
          body{font-family:system-ui,sans-serif;margin:0;padding:24px;background:#121212;color:#eee}
          main{max-width:420px;margin:auto}
          h1{font-size:1.4em}h2{font-size:1em;color:#aaa;font-weight:normal}
          label{display:block;margin:12px 0;font-size:.9em;color:#bbb}
          input{display:block;width:100%;box-sizing:border-box;margin-top:4px;padding:12px;font-size:1em;border-radius:8px;border:1px solid #444;background:#1e1e1e;color:#eee}
          button,.button{display:block;width:100%;box-sizing:border-box;margin-top:16px;padding:14px;font-size:1em;border:0;border-radius:8px;background:#03a9f4;color:#000;text-align:center;text-decoration:none}
          .ha{background:#41bdf5}.or{text-align:center;color:#888;margin-top:24px}.error{color:#ff8a80}
        </style></head><body><main>
        <h1>${texts.title.html()}</h1><h2>${serverName.html()}</h2>
        ${if (raw) content else "<p>${content.html()}</p>"}
        </main></body></html>
    """.trimIndent()

    private fun OutputStream.respond(status: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        write(
            ("HTTP/1.1 $status ${statusText(status)}\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n")
                .toByteArray(),
        )
        write(bytes)
        flush()
    }

    private fun OutputStream.redirect(location: String) {
        write(
            ("HTTP/1.1 302 Found\r\nLocation: $location\r\nCache-Control: no-store\r\n" +
                "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        flush()
    }

    private fun statusText(status: Int) = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        403 -> "Forbidden"
        else -> "Not Found"
    }

    private companion object {
        const val SECRET_BYTES = 16
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_BODY = 8_192

        // A fixed port first, so the address stays the same from one sign-in to the next.
        val PORTS = listOf(8766, 8767, 8768, 0)
    }
}

private fun BufferedInputStream.readLine(): String? {
    val line = StringBuilder()
    while (true) {
        val c = read()
        if (c < 0) return line.takeIf { it.isNotEmpty() }?.toString()
        if (c == '\n'.code) return line.toString().trimEnd('\r')
        if (line.length > MAX_LINE) return null
        line.append(c.toChar())
    }
}

private fun parseForm(text: String): Map<String, String> = text.split('&')
    .filter { it.isNotEmpty() }
    .associate { pair ->
        val name = pair.substringBefore('=')
        val value = pair.substringAfter('=', "")
        runCatching { URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8") }
            .getOrDefault(name to value)
    }

private fun String.html() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private const val MAX_LINE = 8_192
