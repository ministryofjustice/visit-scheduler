package uk.gov.justice.digital.hmpps.visitscheduler.config

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
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
import uk.gov.justice.digital.hmpps.visitscheduler.exception.MatchSessionTemplateToMigratedVisitException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.OverCapacityException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.SupportNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VSiPValidationException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitToMigrateException
import uk.gov.justice.digital.hmpps.visitscheduler.service.PublishEventException
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.service.TemplateNotFoundException

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
      .status(e.statusCode)
      .body(e.responseBodyAsByteArray)
  }

  @ExceptionHandler(WebClientException::class)
  fun handleWebClientException(e: WebClientException): ResponseEntity<ErrorResponse> {
    log.error("Unexpected exception", e)
    val error = ErrorResponse(
      status = HttpStatus.INTERNAL_SERVER_ERROR,
      developerMessage = e.message,
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
      developerMessage = e.message,
    )
    sendErrorTelemetry(TelemetryVisitEvents.BAD_REQUEST_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
  }

  @ExceptionHandler(MatchSessionTemplateToMigratedVisitException::class)
  fun handleMatchSessionTemplateToMigratedVisitException(e: Exception): ResponseEntity<ErrorResponse> {
    log.error("Migration exception: {}", e.message)
    val error = ErrorResponse(
      status = HttpStatus.BAD_REQUEST,
      userMessage = "Migration failure: could not find matching session template",
      developerMessage = e.message,
    )
    sendErrorTelemetry(TelemetryVisitEvents.BAD_REQUEST_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
  }

  @ExceptionHandler(VisitToMigrateException::class)
  fun handleVisitToMigrateException(e: Exception): ResponseEntity<ErrorResponse> {
    log.error("Migration exception: {}", e.message)
    val error = ErrorResponse(
      status = HttpStatus.BAD_REQUEST,
      userMessage = "Migration failure: Could not migrate visit",
      developerMessage = e.message,
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
      developerMessage = (e.message),
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
      developerMessage = e.message,
    )
    sendErrorTelemetry(TelemetryVisitEvents.BAD_REQUEST_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException::class)
  fun handleMethodArgumentException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
    log.debug("Validation exception: {}", e.message)

    val error = ErrorResponse(
      status = HttpStatus.BAD_REQUEST,
      userMessage = "Invalid Argument: ${e.propertyName}",
      developerMessage = e.localizedMessage,
    )
    sendErrorTelemetry(TelemetryVisitEvents.BAD_REQUEST_ERROR_EVENT.eventName, error)
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
  }

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleMethodArgumentException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
    log.debug("Validation exception: {}", e.message)

    val error = ErrorResponse(
      status = HttpStatus.BAD_REQUEST,
      userMessage = if (e.errorCount > 1) "Invalid Arguments" else "Invalid Argument",
      developerMessage = e.localizedMessage,
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
          developerMessage = e.message,
        ),
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
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(OverCapacityException::class)
  fun handleOverCapacityException(e: OverCapacityException): ResponseEntity<ErrorResponse?>? {
    log.debug("Over capacity exception caught : {}", e.message)
    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(
        ErrorResponse(
          status = HttpStatus.BAD_REQUEST,
          userMessage = "Over capacity for time slot",
          developerMessage = e.message,
        ),
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
          developerMessage = e.message,
        ),
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
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(PublishEventException::class)
  fun handlePublishEventException(e: PublishEventException): ResponseEntity<ErrorResponse?>? {
    log.error("Publish event exception caught: {}", e.message)
    val error = ErrorResponse(
      status = HttpStatus.INTERNAL_SERVER_ERROR,
      userMessage = "Failed to publish event: ${e.cause?.message}",
      developerMessage = e.message,
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
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(VSiPValidationException::class)
  fun handleVSiPValidationException(e: VSiPValidationException): ResponseEntity<ValidationErrorResponse?>? {
    log.error("Validation exception", e)
    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(
        ValidationErrorResponse(
          validationMessages = e.messages.asList(),
        ),
      )
  }

  @ExceptionHandler(java.lang.Exception::class)
  fun handleException(e: java.lang.Exception): ResponseEntity<ErrorResponse?>? {
    log.error("Unexpected exception", e)
    val error = ErrorResponse(
      status = HttpStatus.INTERNAL_SERVER_ERROR,
      developerMessage = e.message,
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
        "cause" to (error.userMessage?.take(MAX_ERROR_LENGTH) ?: ""),
      ),
      null,
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
  val developerMessage: String? = null,
) {
  constructor(
    status: HttpStatus,
    errorCode: Int? = null,
    userMessage: String? = null,
    developerMessage: String? = null,
  ) :
    this(status.value(), errorCode, userMessage, developerMessage)
}

data class ValidationErrorResponse(
  val validationMessages: List<String>,
)
