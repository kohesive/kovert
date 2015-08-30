package uy.kohesive.kovert.vertx

import io.vertx.core.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import kotlin.reflect.KClass


/**
 *  VertxInit.ensure() is called from methods that startup vertx to be sure Kovenant and Vertx are configured to work
 *  together.  If you do not startup vertx using these methods, you should directly call VertxInit.ensure() at the start
 *  of your application.  Not all vertx+Kovenant methods call VertxInit.ensure, although those that will not affect performance
 *  might.
 */

public class WrappedThrowableException(cause: Throwable): Exception(cause.getMessage(), cause)

/**
 * Start vert.x returning a Kovenant Promise<Vertx, Exception>
 */
public fun vertx(): Promise<Vertx, Exception> {
    VertxInit.ensure()

    val deferred = deferred<Vertx, Exception>()
    try {
        deferred.resolve(Vertx.vertx())
    } catch (ex: Exception) {
        deferred.reject(ex)
    } catch (ex: Throwable) {
        deferred.reject(WrappedThrowableException(ex))
    }
    return deferred.promise
}

/**
 * Start vert.x returning a Kovenant Promise<Vertx, Exception>
 */
public fun vertx(options: VertxOptions): Promise<Vertx, Exception> {
    VertxInit.ensure()

    val deferred = deferred<Vertx, Exception>()
    try {
        deferred.resolve(Vertx.vertx(options))
    } catch (ex: Exception) {
        deferred.reject(ex)
    }  catch (ex: Throwable) {
        deferred.reject(WrappedThrowableException(ex))
    }
    return deferred.promise
}

/**
 * Start clustered vert.x returning a Kovenant Promise<Vertx, Exception>
 */
public fun vertxCluster(options: VertxOptions): Promise<Vertx, Exception> {
    VertxInit.ensure()

    val deferred = deferred<Vertx, Exception>()
    Vertx.clusteredVertx(options, promiseResult(deferred))
    return deferred.promise
}

/**
 * retrieve the current vert.x context if one is attached to the current thread
 */
public fun vertxContext(): Context? = Vertx.currentContext()

/**
 * Deploy a verticle, returning a Promise<deploymentId as String, Exception>
 */
public fun Vertx.promiseDeployVerticle(verticle: Verticle): Promise<String, Exception> {
    VertxInit.ensure()

    val deferred = deferred<String, Exception>()
    deployVerticle(verticle, promiseResult(deferred))
    return deferred.promise
}

/**
 * Deploy a verticle, returning a Promise<deploymentId as String, Exception>
 */
public fun Vertx.promiseDeployVerticle(verticle: Verticle, options: DeploymentOptions): Promise<String, Exception> {
    VertxInit.ensure()

    val deferred = deferred<String, Exception>()
    deployVerticle(verticle, options, promiseResult(deferred))
    return deferred.promise
}

/**
 * Deploy a verticle, returning a Promise<deploymentId as String, Exception>
 */
public fun  <T : AbstractVerticle> Vertx.promiseDeployVerticle(verticleClass: KClass<T>): Promise<String, Exception> {
    VertxInit.ensure()

    val deferred = deferred<String, Exception>()
    deployVerticle(verticleClass.java.getName(), promiseResult(deferred))
    return deferred.promise
}

/**
 * Deploy a verticle, returning a Promise<deploymentId as String, Exception>
 */
public fun  <T : AbstractVerticle> Vertx.promiseDeployVerticle(verticleClass: KClass<T>, options: DeploymentOptions): Promise<String, Exception> {
    VertxInit.ensure()

    val deferred = deferred<String, Exception>()
    deployVerticle(verticleClass.java.getName(), options, promiseResult(deferred))
    return deferred.promise
}

/**
 * Deploy a verticle, returning a Promise<deploymentId as String, Exception>
 */
public fun  <T : AbstractVerticle> Vertx.promiseDeployVerticle(verticleClass: Class<T>): Promise<String, Exception> {
    VertxInit.ensure()

    val deferred = deferred<String, Exception>()
    deployVerticle(verticleClass.getName(), promiseResult(deferred))
    return deferred.promise
}

/**
 * Deploy a verticle, returning a Promise<deploymentId as String, Exception>
 */
public fun  <T : AbstractVerticle> Vertx.promiseDeployVerticle(verticleClass: Class<T>, options: DeploymentOptions): Promise<String, Exception> {
    VertxInit.ensure()

    val deferred = deferred<String, Exception>()
    deployVerticle(verticleClass.getName(), options, promiseResult(deferred))
    return deferred.promise
}

/**
 * Deploy a verticle, returning a Promise<deploymentId as String, Exception>
 */
public fun Vertx.promiseDeployVerticle(name: String): Promise<String, Exception> {
    VertxInit.ensure()

    val deferred = deferred<String, Exception>()
    deployVerticle(name, promiseResult(deferred))
    return deferred.promise
}

/**
 * Deploy a verticle, returning a Promise<deploymentId as String, Exception>
 */
public fun Vertx.promiseDeployVerticle(name: String, options: DeploymentOptions): Promise<String, Exception> {
    VertxInit.ensure()

    val deferred = deferred<String, Exception>()
    deployVerticle(name, options, promiseResult(deferred))
    return deferred.promise
}

/**
 * Deploy a verticle async without waiting for it to complete
 */
public fun <T : AbstractVerticle> Vertx.deployVerticle(verticleClass: KClass<T>): Unit {
    deployVerticle(verticleClass.java.getName())
}

/**
 * Deploy a verticle async without waiting for it to complete
 */
public fun <T : AbstractVerticle> Vertx.deployVerticle(verticleClass: Class<T>): Unit {
    deployVerticle(verticleClass.getName())
}

/**
 * Undeploy a verticle, returning a Promise<Void, Exception>
 */
public fun Vertx.promiseUndeploy(deploymentId: String): Promise<Void, Exception> {
    VertxInit.ensure()

    val deferred = deferred<Void, Exception>()
    this.undeploy(deploymentId, promiseResult(deferred))
    return deferred.promise
}

/**
 * Close vertx, returning a Promise<Void, Exception>
 */
public fun Vertx.promiseClose(): Promise<Void, Exception> {
    val deferred = deferred<Void, Exception>()
    this.close(promiseResult(deferred))
    return deferred.promise
}

/**
 * Execute blocking code using vertx dispatcher returning a Promise<T, Exception>.  Since Kovenant and
 * vertx dispatching are united, this is the same as doing async { ... } in Kovenant, no need to call on a
 * vertx instance.
 */
public fun <T: Any> Vertx.promiseExecuteBlocking(blockingCode: () -> T): Promise<T, Exception> {
    VertxInit.ensure()

    val deferred = deferred<T, Exception>()
    this.executeBlocking({ response ->
        try {
            response.complete(blockingCode())
        } catch (ex: Throwable) {
            response.fail(ex)
        }
    }, promiseResult(deferred))
    return deferred.promise
}

/**
 * Execute blocking code using vertx dispatcher returning a Promise<T, Exception>.  Since Kovenant and
 * vertx dispatching are united, this is the same as doing async { ... } in Kovenant, no need to call on a
 * vertx instance.
 */
public fun <T: Any> Vertx.executeBlocking(blockingCode: () -> T): Promise<T, Exception> {
    VertxInit.ensure()

    val deferred = deferred<T, Exception>()
    this.executeBlocking({ response ->
        try {
            response.complete(blockingCode())
        } catch (ex: Throwable) {
            response.fail(ex)
        }
    }, promiseResult(deferred))
    return deferred.promise
}

