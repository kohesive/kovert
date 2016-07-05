package uy.kohesive.kovert.core

interface TemplateEngine {
    fun render(template: String, model: Any): String
}

/**
 * Alternative to using the Rendered annotation with a static template name, you can use Rendered annotation AND return
 * a ModelAndTemplateRendering (or promise of one) when the code will programmatically select the template.
 * The prebuilt ModelAndRenderTemplate class implements the ModelAndTemplateRendering interface but any class can do the same.
 */
interface ModelAndTemplateRendering<T : Any> {
    val model: T
    val template: String
}

class ModelAndRenderTemplate<T : Any>(override val model: T, override val template: String) : ModelAndTemplateRendering<T>
