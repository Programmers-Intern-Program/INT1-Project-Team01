package back.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatMessageTest {

    @Test
    @DisplayName("사용자 메시지는 ChatSession 기준으로 저장된다")
    void user_storesSessionMessage() {
        // when
        ChatMessage message = ChatMessage.user(1L, 2L, "  안녕  ");

        // then
        assertThat(message.getWorkspaceId()).isEqualTo(1L);
        assertThat(message.getChatSessionId()).isEqualTo(2L);
        assertThat(message.getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(message.getContent()).isEqualTo("안녕");
        assertThat(message.getTaskId()).isNull();
        assertThat(message.getTaskExecutionId()).isNull();
    }

    @Test
    @DisplayName("Assistant 메시지는 Task 실행 결과와 연결할 수 있다")
    void assistantForTask_storesTaskReferences() {
        // when
        ChatMessage message = ChatMessage.assistantForTask(1L, 2L, 3L, 4L, "작업 완료");

        // then
        assertThat(message.getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(message.getTaskId()).isEqualTo(3L);
        assertThat(message.getTaskExecutionId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("Assistant 메시지는 Orchestration 계획과 연결할 수 있다")
    void assistantForOrchestration_storesOrchestrationReference() {
        // when
        ChatMessage message = ChatMessage.assistantForOrchestration(1L, 2L, 5L, "계획 완료");

        // then
        assertThat(message.getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(message.getOrchestrationPlanId()).isEqualTo(5L);
        assertThat(message.getTaskId()).isNull();
        assertThat(message.getTaskExecutionId()).isNull();
    }

    @Test
    @DisplayName("저장된 메시지는 이후 Task와 연결할 수 있다")
    void linkTask_storesTaskReferences() {
        // given
        ChatMessage message = ChatMessage.user(1L, 2L, "작업으로 전환할 메시지");

        // when
        message.linkTask(3L, null);

        // then
        assertThat(message.getTaskId()).isEqualTo(3L);
        assertThat(message.getTaskExecutionId()).isNull();
    }

    @Test
    @DisplayName("TaskExecution 연결은 null에서 값으로 한 번만 갱신할 수 있다")
    void linkTask_upgradesTaskExecutionIdOnce() {
        // given
        ChatMessage message = ChatMessage.user(1L, 2L, "작업으로 전환할 메시지");
        message.linkTask(3L, null);

        // when
        message.linkTask(3L, 4L);

        // then
        assertThat(message.getTaskId()).isEqualTo(3L);
        assertThat(message.getTaskExecutionId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("이미 연결된 TaskExecution은 삭제하거나 변경할 수 없다")
    void linkTask_alreadyLinkedToTaskExecution_throwsException() {
        // given
        ChatMessage message = ChatMessage.user(1L, 2L, "작업으로 전환할 메시지");
        message.linkTask(3L, 4L);

        // when & then
        assertThatThrownBy(() -> message.linkTask(3L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("task execution");
        assertThatThrownBy(() -> message.linkTask(3L, 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("task execution");
    }

    @Test
    @DisplayName("이미 다른 Task와 연결된 메시지는 다른 Task로 바꿀 수 없다")
    void linkTask_alreadyLinkedToAnotherTask_throwsException() {
        // given
        ChatMessage message = ChatMessage.user(1L, 2L, "작업으로 전환할 메시지");
        message.linkTask(3L, null);

        // when & then
        assertThatThrownBy(() -> message.linkTask(4L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    @DisplayName("이미 다른 Orchestration 계획과 연결된 메시지는 다른 계획으로 바꿀 수 없다")
    void linkOrchestrationPlan_alreadyLinkedToAnotherPlan_throwsException() {
        // given
        ChatMessage message = ChatMessage.assistantForOrchestration(1L, 2L, 5L, "계획 완료");

        // when & then
        assertThatThrownBy(() -> message.linkOrchestrationPlan(6L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orchestration plan");
    }

    @Test
    @DisplayName("메시지 본문은 필수값이다")
    void user_blankContent_throwsException() {
        assertThatThrownBy(() -> ChatMessage.user(1L, 2L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }
}
