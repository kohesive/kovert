package uy.kohesive.kovert.template.freemarker

import freemarker.template.Configuration
import uy.kohesive.kovert.core.TemplateEngine
import java.io.StringWriter

public class KovertFreemarkerTemplateEngine(val freemarker: Configuration) : TemplateEngine {
    override fun render(template: String, model: Any): String {
        val compiledTemplate = freemarker.getTemplate(template)
        StringWriter().use { writer ->
            compiledTemplate.process(model, writer)
            return writer.getBuffer().toString()
        }
    }
}