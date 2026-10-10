package cz.gameshelf.app.di

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class AppScopeTest {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    @After
    fun tearDown() = Thread.setDefaultUncaughtExceptionHandler(defaultHandler)

    @Test
    fun `an exception escaping a coroutine of the app scope does not crash the app`() = runBlocking {
        val crashes = CopyOnWriteArrayList<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> crashes += e }
        val scope = createAppScope()

        scope.launch { error("a bug in some background work") }.join()

        assertEquals(emptyList<Throwable>(), crashes)
        // The scope keeps working.
        assertEquals("still running", scope.async { "still running" }.await())
    }
}
