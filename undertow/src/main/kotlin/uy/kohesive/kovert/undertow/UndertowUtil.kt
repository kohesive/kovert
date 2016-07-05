package uy.kohesive.kovert.undertow


public fun setupUndertowLoggingToSlf4j() {
    // must be called before Undertow is initialized
    System.setProperty("org.jboss.logging.provider", "slf4j")
}