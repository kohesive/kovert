package uy.kohesive.kovert.vertx

import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.json.Json
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.LoggerFactory as Slf4jLoggerFactory

/**
 * This Injekt Module does a few things:
 *
 * Make sure Vertx is setup to work with Kovenant for promises
 * Add a singleton for Jackson object mapper sharing it with the Vertx singleton
 * Setup logger to use the Vertx logging so logging is consistent with vertx
 */
public object VertxInjektables : VertxInjektModule() {
    override fun InjektRegistrar.registerInjectables() {
        common()
        addLoggerFactory<Logger>({ name -> LoggerFactory.getLogger(name) }, { klass -> LoggerFactory.getLogger(klass) })
    }
}

/**
 * Same as VertxInjektables but routes Vert.x and Hazelcast logging facades to use Slf4j, and app directly to Slf4j
 */
public object VertxWithSlf4jInjektables : VertxInjektModule() {
    override fun InjektRegistrar.registerInjectables() {
        setupVertxLoggingToSlf4j()
        common()
        addLoggerFactory<Slf4jLogger>({ name -> Slf4jLoggerFactory.getLogger(name) }, { klass -> Slf4jLoggerFactory.getLogger(klass) })
    }
}

public abstract class VertxInjektModule : InjektModule {
    fun InjektRegistrar.common() {
        VertxInit.ensure()
        addSingleton<ObjectMapper>(Json.mapper)
    }
}
