package app.nodx.domain

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cancel contract ChatViewModel's stop-wind-down relies on:
 * cancel() closes the stream without a Done event, so the collect ends
 * normally and the partial reply gets persisted. */
class FakeEngineCancelTest {

    @Test
    fun `cancel mid-stream closes without Done and keeps partial`() = runTest {
        val engine = FakeEngine()
        engine.load(LoadRequest.Local(modelPath = "/unused"))

        val events = mutableListOf<TokenEvent>()
        val flow = engine.generate(GenerateRequest(messages = listOf(EngineMessage(EngineRole.user, "hi"))))

        // Collect until the first delta, then cancel mid-stream.
        var count = 0
        flow.collect { event ->
            events += event
            if (event is TokenEvent.Delta && ++count == 2) engine.cancel()
        }
        // collect completes normally (no exception) — that is the contract.

        assertTrue(events.any { it is TokenEvent.Delta })
        assertTrue("cancel must not emit Done", events.none { it is TokenEvent.Done })
        assertTrue("cancel must not emit Failed", events.none { it is TokenEvent.Failed })
    }

    @Test
    fun `cancel when idle is safe`() = runTest {
        val engine = FakeEngine()
        engine.cancel()
        val events = engine.generate(
            GenerateRequest(messages = listOf(EngineMessage(EngineRole.user, "hi")))
        ).toList()
        // A fresh generate after an idle cancel still streams fully.
        assertTrue(events.any { it is TokenEvent.Done })
        assertEquals(true, events.filterIsInstance<TokenEvent.Delta>().isNotEmpty())
    }
}
