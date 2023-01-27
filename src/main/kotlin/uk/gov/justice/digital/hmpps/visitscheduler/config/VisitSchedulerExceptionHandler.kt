package uk.gov.justice.digital.hmpps.visitscheduler.config

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.CapacityNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ItemNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.SupportNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.service.PublishEventException
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.service.TemplateNotFoundException
import javax.validation.ValidationException

@RestControllerAdvice
class VisitSchedulerExceptionHandler(
  private val telemetryClient: TelemetryClient,
) {
  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> {
    log.debug("Forbidden (403) returned with message {}", e.message)
    val error = ErrorResponse(
      status = HttpStatus.FORBIDDEN,
      userMessage = "Access denied",
    )
    sendErrorTelemetry(TelemetryVisitEvents.ACCESS_DENIED_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error)
  }

  @ExceptionHandler(WebClientResponseException::class)
  fun handleWebClientResponseException(e: WebClientResponseException): ResponseEntity<ByteArray> {
    if (e.statusCode.is4xxClientError) {
      log.debug("Unexpected client exception with message {}", e.message)
    } else {
      log.error("Unexpected server exception", e)
    }
    return ResponseEntity
      .status(e.rawStatusCode)
      .body(e.responseBodyAsByteArray)
  }

  @ExceptionHandler(WebClientException::class)
  fun handleWebClientException(e: WebClientException): ResponseEntity<ErrorResponse> {
    log.error("Unexpected exception", e)
    val error = ErrorResponse(
      status = HttpStatus.INTERNAL_SERVER_ERROR,
      developerMessage = e.message
    )
    sendErrorTelemetry(TelemetryVisitEvents.INTERNAL_SERVER_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
  }

  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: Exception): ResponseEntity<ErrorResponse> {
    log.debug("Validation exception: {}", e.message)
    val error = ErrorResponse(
      status = HttpStatus.BAD_REQUEST,
      userMessage = "Validation failure: ${e.cause?.message}",
      developerMessage = e.message
    )
    sendErrorTelemetry(TelemetryVisitEvents.BAD_REQUEST_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
  }

  @ExceptionHandler(MissingServletRequestParameterException::class)
  fun handleValidationException(e: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> {
    log.debug("Bad Request (400) returned {}", e.message)
    val error = ErrorResponse(
      status = (HttpStatus.BAD_REQUEST),
      userMessage = "Missing Request Parameter: ${e.cause?.message}",
      developerMessage = (e.message)
    )
    sendErrorTelemetry(TelemetryVisitEvents.BAD_REQUEST_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
  }

  @ExceptionHandler(HttpMessageNotReadableException::class)
  fun handleJsonMappingValidationException(e: Exception): ResponseEntity<ErrorResponse> {
    log.debug("Validation exception: {}", e.message)
    val error = ErrorResponse(
      status = HttpStatus.BAD_REQUEST,
      userMessage = "Validation failure: ${e.message}",
      developerMessage = e.message
    )
    sendErrorTelemetry(TelemetryVisitEvents.BAD_REQUEST_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException::class, MethodArgumentNotValidException::class)
  fun handleMethodArgumentException(e: Exception): ResponseEntity<ErrorResponse> {
    log.debug("Validation exception: {}", e.message)
    val error = ErrorResponse(
      status = HttpStatus.BAD_REQUEST,
      userMessage = "Invalid Argument: ${e.cause?.message}",
      developerMessage = e.message
    )
    sendErrorTelemetry(TelemetryVisitEvents.BAD_REQUEST_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
  }

  @ExceptionHandler(VisitNotFoundException::class)
  fun handleVisitNotFoundException(e: VisitNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Visit not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          userMessage = "Visit not found: ${e.cause?.message}",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(CapacityNotFoundException::class)
  fun handleCapacityNotFoundException(e: CapacityNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Capacity not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          userMessage = "Capacity not found",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(TemplateNotFoundException::class)
  fun handleTemplateNotFoundException(e: TemplateNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Template not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          userMessage = "Template not found: ${e.cause?.message}",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(SupportNotFoundException::class)
  fun handleSupportNotFoundException(e: SupportNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Support not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(
        ErrorResponse(
          status = HttpStatus.BAD_REQUEST,
          userMessage = "Support not found: ${e.cause?.message}",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(PublishEventException::class)
  fun handlePublishEventException(e: PublishEventException): ResponseEntity<ErrorResponse?>? {
    log.error("Publish event exception caught: {}", e.message)
    val error = ErrorResponse(
      status = HttpStatus.INTERNAL_SERVER_ERROR,
      userMessage = "Failed to publish event: ${e.cause?.message}",
      developerMessage = e.message
    )
    sendErrorTelemetry(TelemetryVisitEvents.PUBLISH_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
  }

  @ExceptionHandler(ItemNotFoundException::class)
  fun handleItemNotFoundException(e: ItemNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          userMessage = "Not found",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(java.lang.Exception::class)
  fun handleException(e: java.lang.Exception): ResponseEntity<ErrorResponse?>? {
    log.error("Unexpected exception", e)
    val error = ErrorResponse(
      status = HttpStatus.INTERNAL_SERVER_ERROR,
      developerMessage = e.message
    )
    sendErrorTelemetry(TelemetryVisitEvents.INTERNAL_SERVER_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
  }

  private fun sendErrorTelemetry(name: String, error: ErrorResponse) {
    telemetryClient.trackEvent(
      name,
      mapOf(
        "status" to error.status.toString(),
        "message" to (error.developerMessage?.take(MAX_ERROR_LENGTH) ?: ""),
        "cause" to (error.userMessage?.take(MAX_ERROR_LENGTH) ?: "")
      ),
      null
    )
  }

  companion object {
    const val MAX_ERROR_LENGTH = 256
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class ErrorResponse(
  val status: Int,
  val errorCode: Int? = null,
  val userMessage: String? = null,
  val developerMessage: String? = null
) {
  constructor(
    status: HttpStatus,
    errorCode: Int? = null,
    userMessage: String? = null,
    developerMessage: String? = null
  ) :
    this(status.value(), errorCode, userMessage, developerMessage)
}
