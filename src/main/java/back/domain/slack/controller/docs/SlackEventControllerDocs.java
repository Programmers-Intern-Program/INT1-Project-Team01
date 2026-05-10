package back.domain.slack.controller.docs;

import back.domain.slack.dto.request.SlackEventReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Slack Event", description = "Slack Webhook 이벤트 수신 API")
public interface SlackEventControllerDocs {

    @Operation(
            summary = "Slack 이벤트 수신",
            description = "Slack Events API로부터 수신되는 Webhook 요청(url_verification 및 event_callback)을 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "이벤트 수신 성공 (Verification Challenge 반환 또는 성공 응답)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "1. url_verification 성공",
                                            value = "{\"challenge\": \"3eZbrw1aBm2rZgRNFpe56la...\"}"),
                                    @ExampleObject(
                                            name = "2. 비즈니스 이벤트 수신 성공",
                                            value = "{\"data\":null,\"message\":\"성공\"}")
                            }
                    )
            )
    })
    ResponseEntity<Object> handleSlackEvent(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Slack Events API Webhook Payload",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "이벤트 콜백 (app_mention)",
                                            value = """
                                                    {
                                                      "type": "event_callback",
                                                      "team_id": "T1234567",
                                                      "event_id": "Ev1234567",
                                                      "event": {
                                                        "type": "app_mention",
                                                        "channel": "C1234567",
                                                        "user": "U1234567",
                                                        "text": "<@U012ABCDEF> 예약해줘",
                                                        "ts": "1715059800.123456"
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "URL 검증 (url_verification)",
                                            value = """
                                                    {
                                                      "type": "url_verification",
                                                      "challenge": "3eZbrw1aBm2rZgRNFpe56laSqXz..."
                                                    }
                                                    """
                                    )
                            }
                    )
            )
            SlackEventReq request);
}