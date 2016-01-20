package uy.kohesive.kovert.core

import uy.klutter.core.common.whenNotNull
import uy.klutter.core.jdk.mustStartWith
import uy.klutter.core.jdk.nullIfBlank


public object KovertConfig {
    /**
     * Default HTTP to method prefix name mathing
     */
    public val defaultVerbAliases: MutableMap<String, PrefixAsVerbWithSuccessStatus> = hashMapOf()

    init {
        addVerbAlias("get", HttpVerb.GET, 200)
        addVerbAlias("list", HttpVerb.GET, 200)
        addVerbAlias("view", HttpVerb.GET, 200)

        addVerbAlias("post", HttpVerb.POST, 200)

        addVerbAlias("delete", HttpVerb.DELETE, 200)
        addVerbAlias("remove", HttpVerb.DELETE, 200)

        addVerbAlias("put", HttpVerb.PUT, 200)

        addVerbAlias("patch", HttpVerb.PATCH, 200)
    }

    public fun addVerbAlias(prefix: String, verb: HttpVerb, successStatusCode: Int = 200): KovertConfig {
        defaultVerbAliases.put(prefix, PrefixAsVerbWithSuccessStatus(prefix, verb, successStatusCode))
        return this
    }

    public fun removeVerbAlias(prefix: String): KovertConfig {
        defaultVerbAliases.remove(prefix)
        return this
    }

    public @Volatile var reportStackTracesOnExceptions: Boolean = false
    public @Volatile var autoAddBodyHandlersOnPutPostPatch: Boolean = true

    public val templateEngines = arrayListOf<RegisteredTemplateEngine>()

    public fun registerTemplateEngine(templateEngine: TemplateEngine, recognizeBySuffix: String, contentType: String = "text/html") {
        registerTemplateEngine(templateEngine, listOf(recognizeBySuffix), contentType)
    }

    public fun registerTemplateEngine(templateEngine: TemplateEngine, recognizeBySuffix: List<String>, contentType: String = "text/html") {
        recognizeBySuffix.forEach {
            templateEngines.add(RegisteredTemplateEngine(it.mustStartWith('.'), contentType, templateEngine))
        }
        // always keep the list longest to shortest extension so more specific matches win
        templateEngines.sortedByDescending { it.recognizeBySuffix.length }
    }

    public fun engineForTemplate(template: String): RegisteredTemplateEngine {
        return template.nullIfBlank().whenNotNull { template ->
            KovertConfig.templateEngines.firstOrNull { template.endsWith(it.recognizeBySuffix) }
        } ?: throw Exception("Cannot find render engine for template '${template}' (see KovertConfig.registerTemplateEngine)")
    }

    data class RegisteredTemplateEngine(val recognizeBySuffix: String, val contentType: String, val templateEngine: TemplateEngine)
}

public data class PrefixAsVerbWithSuccessStatus(val prefix: String, val verb: HttpVerb, val successStatusCode: Int)

// look more at "Good principles of REST design" at http://stackoverflow.com/questions/1619152/how-to-create-rest-urls-without-verbs
