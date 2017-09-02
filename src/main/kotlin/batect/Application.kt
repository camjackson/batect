package batect

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.provider
import batect.cli.CommandLineParser
import batect.cli.DecomposeCommandLineParser
import batect.cli.Failed
import batect.cli.Succeeded
import batect.config.io.ConfigurationLoader
import batect.config.io.PathResolverFactory
import batect.docker.DockerClient
import batect.docker.DockerContainerCreationCommandGenerator
import batect.docker.DockerImageLabellingStrategy
import batect.docker.ProcessRunner
import batect.model.DependencyGraphProvider
import batect.model.TaskStateMachineProvider
import batect.model.steps.TaskStepRunner
import java.io.PrintStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val status = Application(System.out, System.err).run(args.toList())
        exitProcess(status)
    } catch (e: Throwable) {
        System.err.println("Fatal exception: ")
        e.printStackTrace(System.err)
        exitProcess(-1)
    }
}

class Application(override val kodein: Kodein) : KodeinAware {
    constructor(outputStream: PrintStream, errorStream: PrintStream) :
            this(createDefaultKodeinConfiguration(outputStream, errorStream))

    private val errorStream: PrintStream = instance(PrintStreamType.Error)
    private val commandLineParser: CommandLineParser = instance()

    fun run(args: Iterable<String>): Int {
        try {
            val result = commandLineParser.parse(args)

            return when (result) {
                is Failed -> {
                    errorStream.println(result.error)
                    -1
                }
                is Succeeded -> result.command.run()
            }
        } catch (e: Throwable) {
            errorStream.println(e)
            return -1
        }
    }
}

enum class PrintStreamType {
    Output,
    Error
}

private fun createDefaultKodeinConfiguration(outputStream: PrintStream, errorStream: PrintStream): Kodein = Kodein {
    bind<ConfigurationLoader>() with provider { ConfigurationLoader(instance(), instance()) }
    bind<PathResolverFactory>() with provider { PathResolverFactory() }
    bind<FileSystem>() with provider { FileSystems.getDefault() }
    bind<TaskRunner>() with provider { TaskRunner(instance(), instance(), instance(), instance()) }
    bind<DockerClient>() with provider { DockerClient(instance(), instance(), instance()) }
    bind<DockerImageLabellingStrategy>() with provider { DockerImageLabellingStrategy() }
    bind<ProcessRunner>() with provider { ProcessRunner() }
    bind<DockerContainerCreationCommandGenerator>() with provider { DockerContainerCreationCommandGenerator() }
    bind<EventLogger>() with provider { EventLogger(instance()) }
    bind<Console>() with provider { Console(instance(PrintStreamType.Output)) }
    bind<PrintStream>(PrintStreamType.Error) with instance(errorStream)
    bind<PrintStream>(PrintStreamType.Output) with instance(outputStream)
    bind<CommandLineParser>() with provider { DecomposeCommandLineParser(this) }
    bind<TaskStepRunner>() with provider { TaskStepRunner(instance()) }
    bind<DependencyGraphProvider>() with provider { DependencyGraphProvider() }
    bind<TaskStateMachineProvider>() with provider { TaskStateMachineProvider() }
}
