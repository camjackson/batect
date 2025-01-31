/*
   Copyright 2017-2019 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.ui

import batect.execution.CleanupOption
import batect.execution.RunOptions
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartFailedEvent
import batect.execution.model.events.ContainerStopFailedEvent
import batect.execution.model.events.ExecutionFailedEvent
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.TemporaryDirectoryDeletionFailedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent
import batect.execution.model.events.UserInterruptedExecutionEvent
import batect.os.SystemInfo
import batect.ui.text.Text
import batect.ui.text.TextRun
import batect.ui.text.join

class FailureErrorMessageFormatter(systemInfo: SystemInfo) {
    private val newLine = systemInfo.lineSeparator

    fun formatErrorMessage(event: TaskFailedEvent, runOptions: RunOptions): TextRun = when (event) {
        is TaskNetworkCreationFailedEvent -> formatErrorMessage("Could not create network for task", event.message)
        is ImageBuildFailedEvent -> formatErrorMessage("Could not build image from directory '${event.source.buildDirectory}'", event.message)
        is ImagePullFailedEvent -> formatErrorMessage(Text("Could not pull image ") + Text.bold(event.source.imageName), event.message)
        is ContainerCreationFailedEvent -> formatErrorMessage(Text("Could not create container ") + Text.bold(event.container.name), event.message)
        is ContainerStartFailedEvent -> formatErrorMessage(Text("Could not start container ") + Text.bold(event.container.name), event.message) + hintToReRunWithCleanupDisabled(runOptions)
        is ContainerDidNotBecomeHealthyEvent -> formatErrorMessage(Text("Container ") + Text.bold(event.container.name) + Text(" did not become healthy"), event.message) + hintToReRunWithCleanupDisabled(runOptions)
        is ContainerRunFailedEvent -> formatErrorMessage(Text("Could not run container ") + Text.bold(event.container.name), event.message)
        is ContainerStopFailedEvent -> formatErrorMessage(Text("Could not stop container ") + Text.bold(event.container.name), event.message)
        is ContainerRemovalFailedEvent -> formatErrorMessage(Text("Could not remove container ") + Text.bold(event.container.name), event.message)
        is TaskNetworkDeletionFailedEvent -> formatErrorMessage("Could not delete the task network", event.message)
        is TemporaryFileDeletionFailedEvent -> formatErrorMessage("Could not delete temporary file '${event.filePath}'", event.message)
        is TemporaryDirectoryDeletionFailedEvent -> formatErrorMessage("Could not delete temporary directory '${event.directoryPath}'", event.message)
        is ExecutionFailedEvent -> formatErrorMessage("An unexpected exception occurred during execution", event.message)
        is UserInterruptedExecutionEvent -> formatErrorMessage("Interrupt received during execution", "User interrupted execution.")
    }

    private fun formatErrorMessage(headline: String, body: String) = formatErrorMessage(TextRun(headline), body)
    private fun formatErrorMessage(headline: TextRun, body: String) = Text.red(Text.bold("Error: ") + headline + Text(".$newLine")) + Text(body)

    private fun hintToReRunWithCleanupDisabled(runOptions: RunOptions): TextRun = when (runOptions.behaviourAfterFailure) {
        CleanupOption.Cleanup -> Text("$newLine${newLine}You can re-run the task with ") + Text.bold("--no-cleanup-after-failure") + Text(" to leave the created containers running to diagnose the issue.")
        CleanupOption.DontCleanup -> TextRun("")
    }

    fun formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events: Set<TaskEvent>, cleanupCommands: List<String>): TextRun {
        val containerCreationEvents = events.filterIsInstance<ContainerCreatedEvent>()

        if (containerCreationEvents.isEmpty()) {
            throw IllegalArgumentException("No containers were created and so this method should not be called.")
        }

        if (cleanupCommands.isEmpty()) {
            throw IllegalArgumentException("No cleanup commands were provided.")
        }

        val containerMessages = containerCreationEvents
            .sortedBy { it.container.name }
            .map { event -> Text("For container ") + Text.bold(event.container.name) + Text(", view its output by running '") + Text.bold("docker logs ${event.dockerContainer.id}") + Text("', or run a command in the container with '") + Text.bold("docker exec -it ${event.dockerContainer.id} <command>") + Text("'.$newLine") }
            .join()

        val formattedCommands = cleanupCommands.joinToString(newLine)

        return Text.red(Text("As the task was run with ") + Text.bold("--no-cleanup-after-failure") + Text(", the created containers will not be cleaned up.$newLine")) +
            containerMessages +
            Text(newLine) +
            Text("Once you have finished investigating the issue, clean up all temporary resources created by batect by running:$newLine") +
            Text.bold(formattedCommands)
    }

    fun formatManualCleanupMessageAfterCleanupFailure(cleanupCommands: List<String>): TextRun {
        if (cleanupCommands.isEmpty()) {
            return TextRun()
        }

        val instruction = if (cleanupCommands.size == 1) {
            "You may need to run the following command to clean up any remaining resources:"
        } else {
            "You may need to run some or all of the following commands to clean up any remaining resources:"
        }

        val formattedCommands = cleanupCommands.joinToString(newLine)

        return Text.red("Clean up has failed, and batect cannot guarantee that all temporary resources created have been completely cleaned up.$newLine") +
            Text(instruction) + Text(newLine) +
            Text.bold(formattedCommands)
    }
}
