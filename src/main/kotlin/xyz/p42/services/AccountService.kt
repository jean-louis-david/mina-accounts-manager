package xyz.p42.services

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.serialization.encodeToString
import xyz.p42.accounts
import xyz.p42.accountsToBeReleased
import xyz.p42.graphQlEndpoint
import xyz.p42.json
import xyz.p42.model.Account
import xyz.p42.model.Message
import xyz.p42.plugins.logger
import xyz.p42.properties.ACCOUNTS_TO_KEEP_UNUSED
import xyz.p42.properties.IS_REGULAR_ACCOUNT_QUERY_PARAM
import xyz.p42.properties.UNLOCK_ACCOUNT_QUERY_PARAM
import xyz.p42.utils.getAccountVerificationKey
import xyz.p42.utils.isEndpointAvailable
import xyz.p42.utils.lockAccount
import xyz.p42.utils.releaseAccountAndGetNextIndex
import xyz.p42.utils.unlockAccount
import kotlin.random.Random

suspend fun accountAcquisitionHandler(call: ApplicationCall) =
  try {
    val isRegularAccount = call.request.queryParameters[IS_REGULAR_ACCOUNT_QUERY_PARAM]?.toBoolean() ?: true
    val unlockAccount = call.request.queryParameters[UNLOCK_ACCOUNT_QUERY_PARAM]?.toBoolean() ?: false
    var index = Random.nextInt(0, accounts.size - ACCOUNTS_TO_KEEP_UNUSED)
    while (true) {
      if (isRegularAccount && isEndpointAvailable(graphQlEndpoint)) {
        logger.info("An attempt to acquire non-zkApp account...")
        if (
          accounts[index].sk != null &&
          getAccountVerificationKey(accounts[index]) == null &&
          !accounts[index].used
        ) {
          break
        }
        logger
          .info(
            "Account with index #${index} is already in use or this is the zkApp account when it is not expected, or there is no 'sk' property available for account!"
          )
      } else {
        logger.info("An attempt to acquire any account...")
        if (
          accounts[index].sk != null &&
          !accounts[index].used
        ) {
          break
        }
        logger
          .info(
            "Account with index #${index} is already in use or there is no 'sk' property available for account!"
          )
      }
      index = releaseAccountAndGetNextIndex()
    }
    accounts[index].used = true
    logger
      .info(
        "Acquired account with Index #${index} and public key ${accounts[index].pk}"
      )
    if (unlockAccount) {
      logger.info("Unlocking account with index #${index}...")
      unlockAccount(accounts[index])
    }
    call.respondText(
      text = json.encodeToString(
        value = accounts[index]
      ),
      contentType = ContentType.Application.Json,
      status = HttpStatusCode.OK
    )
  } catch (e: Throwable) {
    call.respondText(
      text = json.encodeToString(
        value = Message(
          code = HttpStatusCode.InternalServerError.value,
          message = e.message!!
        )
      ),
      contentType = ContentType.Application.Json,
      status = HttpStatusCode.InternalServerError
    )
  }

suspend fun accountReleaseHandler(call: ApplicationCall) =
  try {
    val account = json.decodeFromString<Account>(call.receiveText())
    val message = "Account with public key ${account.pk} is set to be released."

    accountsToBeReleased.add(account)
    logger.info(message)

    call.respondText(
      text = json.encodeToString(
        value = Message(
          code = HttpStatusCode.OK.value,
          message = message
        )
      ),
      contentType = ContentType.Application.Json,
      status = HttpStatusCode.OK
    )
  } catch (e: Throwable) {
    call.respondText(
      text = json.encodeToString(
        value = Message(
          code = HttpStatusCode.InternalServerError.value,
          message = e.message!!
        )
      ),
      contentType = ContentType.Application.Json,
      status = HttpStatusCode.InternalServerError
    )
  }

suspend fun accountLockStateHandler(call: ApplicationCall, isLock: Boolean = true) =
  try {
    val account = json.decodeFromString<Account>(call.receiveText())
    val publicKey = if (isLock) {
      lockAccount(account)
    } else {
      unlockAccount(account)
    }
    val message = "Account with public key $publicKey is ${if (!isLock) "un" else ""}locked."

    logger.info(message)
    call.respondText(
      text = json.encodeToString(
        value = Message(
          code = HttpStatusCode.OK.value,
          message = message
        )
      ),
      contentType = ContentType.Application.Json,
      status = HttpStatusCode.OK
    )
  } catch (e: Throwable) {
    call.respondText(
      text = json.encodeToString(
        value = Message(
          code = HttpStatusCode.InternalServerError.value,
          message = e.message!!
        )
      ),
      contentType = ContentType.Application.Json,
      status = HttpStatusCode.InternalServerError
    )
  }
