package back.domain.execution.service;

import back.domain.execution.dto.request.TaskExecutionRunCommand;
import back.domain.execution.dto.response.TaskExecutionRunResult;

public interface TaskExecutionRunner {

    TaskExecutionRunResult run(TaskExecutionRunCommand command);
}
