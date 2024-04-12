package xyz.p42.plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.html.body
import kotlinx.html.code
import kotlinx.html.head
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import xyz.p42.accounts
import xyz.p42.json
import xyz.p42.properties.HTML_CODE_BLOCK_STYLE
import xyz.p42.properties.SERVICE_TITLE
import xyz.p42.services.accountAcquisitionHandler
import xyz.p42.services.accountLockStateHandler
import xyz.p42.services.accountReleaseHandler
import xyz.p42.utils.LoggingUtils
import xyz.p42.utils.getErrorHtml
import xyz.p42.utils.getWelcomeMessage

val logger = LoggingUtils.logger

private val acquireAccountMutex = Mutex()
private val releaseAccountMutex = Mutex()
private val listAcquiredAccountsMutex = Mutex()
private val lockAccountMutex = Mutex()
private val unlockAccountMutex = Mutex()

@ExperimentalSerializationApi
fun Application.configureRouting() {
  install(StatusPages) {
    exception<Throwable> { call, cause ->
      call.respondHtml(status = HttpStatusCode.InternalServerError) {
        getErrorHtml(cause.message!!, this)
      }
    }
    status(HttpStatusCode.NotFound) { call, cause ->
      call.respondHtml(status = HttpStatusCode.NotFound) {
        getErrorHtml(cause.description, this)
      }
    }
    status(HttpStatusCode.MethodNotAllowed) { call, cause ->
      call.respondHtml(status = HttpStatusCode.MethodNotAllowed) {
        getErrorHtml(cause.description, this)
      }
    }
  }

  routing {
    get("/") {
      call.respondHtml {
        head {
          title(SERVICE_TITLE)
        }
        body {
          code {
            style = HTML_CODE_BLOCK_STYLE
            +getWelcomeMessage()
          }
        }
      }
    }
    get("/acquire-account") {
      acquireAccountMutex.withLock {
        accountAcquisitionHandler(call)
      }
    }
    put("/release-account") {
      releaseAccountMutex.withLock {
        accountReleaseHandler(call)
      }
    }
    get("/list-acquired-accounts") {
      listAcquiredAccountsMutex.withLock {
        logger.info("Listing acquired accounts...")
        call.respondText(
          text = json.encodeToString(
            value = accounts.filter { it.used }
          ),
          contentType = ContentType.Application.Json,
          status = HttpStatusCode.OK
        )
      }
    }
    put("/lock-account") {
      lockAccountMutex.withLock {
        accountLockStateHandler(call, isLock = true)
      }
    }
    put("/unlock-account") {
      unlockAccountMutex.withLock {
        accountLockStateHandler(call, isLock = false)
      }
    }
  }
}
