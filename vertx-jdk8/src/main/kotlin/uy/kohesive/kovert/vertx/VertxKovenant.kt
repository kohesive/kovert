package uy.kohesive.kovert.vertx

import io.vertx.core.AsyncResult
import io.vertx.core.Context
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Dispatcher
import nl.komponents.kovenant.DispatcherContext
import nl.komponents.kovenant.Kovenant
import uy.kohesive.kovert.core.knownSimpleTypes
import uy.kohesive.kovert.vertx.internal.VertxKovenantContext
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.temporal.Temporal


/**
 * Setup Kovenant do dispatch via Vert-x, and ensure the Vert.x Jackson object mapper is setup for Kotlin and JDK 8 types
 */

private object VertxInit {
    val fallbackContext = Kovenant.createContext {
        workerContext.dispatcher {
            name = "worker-fallback"
            concurrentTasks = Runtime.getRuntime().availableProcessors()
            pollStrategy {
                //Some intermediate strategies
                yielding(numberOfPolls = 1000)

                //Make sure to block to keep the threads alive
                blocking()
            }
        }


        callbackContext.dispatcher {
            name = "callback-fallback" //that has a nice ring to it too
            concurrentTasks = 1
            pollStrategy {
                //Again, make sure to block to keep the threads alive
                blocking()
            }
        }
    }

    init {
        Kovenant.context = VertxKovenantContext(fallbackContext)
        setupVertxJsonForKotlin()
        // add our JDK 8 simple types to core
        knownSimpleTypes.addAll(listOf(javaClass<Temporal>(), javaClass<OffsetDateTime>(), javaClass<ZonedDateTime>(), javaClass<LocalDateTime>()))
    }

    @suppress("NOTHING_TO_INLINE")
    public inline fun ensure() {
        // TODO: here to be sure we have intiailized anything related before using,
        //       although this function may remain empty it causes initializers on the
        //       object to run.
    }
}

/**
 * Helper to convert an expectation of AsyncResult<T> into a promise represented by Deferred<T, Throwable>
 *
 *     i.e.
 *       public fun someAsynActionAsPromise(): Promise<SomeType, Exception> {
 *           val deferred = deferred<SomeType, Exception>()
 *           vertx.someAsyncAction( promiseResult(deferred) )
 *           return deferred.promise
 *       }
 */
public fun <T: Any> promiseResult(deferred: Deferred<T, Exception>): (AsyncResult<T>) -> Unit {
    return { completion ->
        if (completion.succeeded()) {
            deferred.resolve(completion.result())
        } else {
            if (completion.cause() is Exception) {
                deferred.reject(completion.cause() as Exception)
            } else {
                deferred.reject(WrappedThrowableException(completion.cause()))
            }
        }
    }
}
