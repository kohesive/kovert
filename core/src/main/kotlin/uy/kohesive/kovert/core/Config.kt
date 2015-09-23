package uy.kohesive.kovert.core


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
}

public data class PrefixAsVerbWithSuccessStatus(val prefix: String, val verb: HttpVerb, val successStatusCode: Int)

// look more at "Good principles of REST design" at http://stackoverflow.com/questions/1619152/how-to-create-rest-urls-without-verbs
