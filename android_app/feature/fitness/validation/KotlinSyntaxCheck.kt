import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val environment = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(environment.project, false)
        val files = args.flatMap { File(it).walkTopDown().filter { file -> file.isFile && file.extension in setOf("kt", "kts") }.toList() }
        var errors = 0
        for (file in files) {
            val parsed = factory.createFile(file.name, file.readText().removePrefix("\uFEFF").replace("\r\n", "\n"))
            for (error in PsiTreeUtil.collectElementsOfType(parsed, PsiErrorElement::class.java)) {
                errors++
                val line = parsed.text.substring(0, error.textOffset).count { it == '\n' } + 1
                println("${file.path}:$line ${error.errorDescription}")
            }
        }
        println("Kotlin语法检查：${files.size}个文件，$errors 个语法错误；未进行Android符号或插件解析。")
        check(errors == 0)
    } finally { Disposer.dispose(disposable) }
}
