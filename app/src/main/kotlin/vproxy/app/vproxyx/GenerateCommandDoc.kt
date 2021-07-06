package vproxy.app.vproxyx

import vproxy.app.app.cmd.HelpCommand
import vproxy.app.app.cmd.HelpCommand.ActMan
import vproxy.app.app.cmd.HelpCommand.ResMan
import java.io.File
import kotlin.system.exitProcess

class GenerateCommandDoc {
    companion object {
        @JvmStatic
        fun main0(args: Array<String>) {
            File("./doc/command.md").writeText(helpMarkdown())
            exitProcess(0)
        }

        @JvmStatic
        fun helpMarkdown(): String {
            val result = StringBuilder()
            val men = ResMan.values()
            val general = File("./misc/general.md").readText()
            result.append(
                """
                |# Command
                |
                |$general
                |
                |## Actions
                |
                |${
                    ActMan.values().map {
                        """### ${it.act}
                    |
                    |${it.descr}
                    |
                """.trimMargin()
                    }.joinToString(separator = "\n") { it }
                }
                |## Resources
                |
                |
            """.trimMargin())
            for (man in men) {
                result.append(
                    """
                    |### ${man.res}
                    |${if (man.shortVer != null) "\nshort version: `${man.shortVer}`\n" else ""}
                    |description: ${man.descr}
                    |
                    |#### actions
                    |
                    |""".trimMargin()
                )
                for (act in man.acts) {
                    result.append(
                        """
                        |##### ${act.act.act}
                        |
                        |<details><summary>${act.descr}</summary>
                        |
                        |<br>
                        |
                        |""".trimMargin()
                    )
                    if (act.paramDescr != null && act.paramDescr.isNotEmpty()) {
                        result.append(
                            """
                            |parameters:
                            |
                            ||name|description|opt|default|
                            ||---|---|:---:|---|
                            |
                        """.trimMargin()
                        )
                        val params = act.paramDescr
                        for (param in params) {
                            result.append(
                                """
                                ||${param.param.param}|${param.descr.replace("\n", "<br>")}|${if (param.optional) "Y" else ""}|${param.defaultValue ?: ""}|
                                |
                            """.trimMargin()
                            )
                        }
                        result.append("\n")
                    }
                    if (act.flagDescr != null && act.flagDescr.isNotEmpty()) {
                        result.append(
                            """
                            |flags:
                            |
                            ||name|description|opt|default|
                            ||---|---|:---:|:---:|
                            |
                        """.trimMargin()
                        )
                        val flags = act.flagDescr
                        for (flag in flags) {
                            result.append("|${flag.flag.flag}|${flag.descr.replace("\n", "<br>")}|${if (flag.optional) "Y" else ""}|${if (flag.isDefault) "Y" else ""}|\n")
                        }
                        result.append("\n")
                    }
                    if (act.examples != null && !act.examples.isEmpty()) {
                        result.append("examples:\n\n")
                        for (example in act.examples) {
                            result.append("```\n")
                            result.append("$ ").append(example.left).append("\n")
                            result.append(example.right).append("\n")
                            result.append("```\n\n")
                        }
                    }
                    result.append("</details>\n\n")
                }
            }
            val params = HelpCommand.ParamMan.values().map {
                """
                    |### ${it.param}
                    |${if (it.shortVer != null) "\nshort version: `${it.shortVer}`\n" else ""}
                    |description: ${it.descr}
                    |
                """.trimMargin()
            }.joinToString(separator = "\n") { it }
            result.append(
                """
                |## Params
                |
                |$params
            """.trimMargin()
            )
            val flags = HelpCommand.FlagMan.values().map {
                """
                    |### ${it.flag}
                    |${if (it.shortVer != null) "\nshort version: `${it.shortVer}`\n" else ""}
                    |description: ${it.descr}
                    |
                """.trimMargin()
            }.joinToString(separator = "\n") { it }
            result.append(
                """
                    |
                    |## Flags
                    |
                    |$flags
                    |
                """.trimMargin()
            )
            return result.toString()
        }

    }
}
