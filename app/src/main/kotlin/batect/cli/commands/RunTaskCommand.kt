/*
   Copyright 2017 Charles Korn.

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

package batect.cli.commands

import batect.PrintStreamType
import batect.TaskRunner
import batect.cli.CommonOptions
import batect.cli.options.LevelOfParallelismDefaultValueProvider
import batect.cli.options.ValueConverters
import batect.config.Configuration
import batect.config.Task
import batect.config.io.ConfigurationLoader
import batect.logging.Logger
import batect.logging.logger
import batect.model.TaskExecutionOrderResolutionException
import batect.model.TaskExecutionOrderResolver
import batect.ui.Console
import batect.ui.ConsoleColor
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance

class RunTaskCommandDefinition : CommandDefinition("run", "Run a task.") {
    val taskName: String by RequiredPositionalParameter("TASK", "The name of the task to run.")

    val levelOfParallelism: Int by valueOption(
        "level-of-parallelism",
        "Maximum number of operations to run in parallel.",
        LevelOfParallelismDefaultValueProvider,
        ValueConverters::positiveInteger,
        'p')

    override fun createCommand(kodein: Kodein): Command = RunTaskCommand(
        kodein.instance(CommonOptions.ConfigurationFileName),
        taskName,
        levelOfParallelism,
        kodein.instance(),
        kodein.instance(),
        kodein.instance(),
        kodein.instance(PrintStreamType.Output),
        kodein.instance(PrintStreamType.Error),
        kodein.logger<RunTaskCommand>())
}

data class RunTaskCommand(
    val configFile: String,
    val taskName: String,
    val levelOfParallelism: Int,
    val configLoader: ConfigurationLoader,
    val taskExecutionOrderResolver: TaskExecutionOrderResolver,
    val taskRunner: TaskRunner,
    val console: Console,
    val errorConsole: Console,
    val logger: Logger) : Command {

    override fun run(): Int {
        val config = configLoader.loadConfig(configFile)

        try {
            val tasks = taskExecutionOrderResolver.resolveExecutionOrder(config, taskName)
            return runTasks(config, tasks)

        } catch (e: TaskExecutionOrderResolutionException) {
            logger.error {
                message("Could not resolve task execution order.")
                exception(e)
            }

            errorConsole.withColor(ConsoleColor.Red) {
                println(e.message ?: "")
            }

            return -1
        }
    }

    private fun runTasks(config: Configuration, tasks: List<Task>): Int {
        for (task in tasks) {
            val exitCode = taskRunner.run(config, task, levelOfParallelism)

            if (exitCode != 0) {
                return exitCode
            }

            console.println()
        }

        return 0
    }
}